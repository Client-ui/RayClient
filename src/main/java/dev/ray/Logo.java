package dev.ray;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Ray Client logo (the orbiting R), drawn as real anti-aliased vector shapes with plain rectangles.
 *
 * The artwork was traced from the logo file into a few polygons. For a requested height they are rasterised once
 * (4 sub-scanlines per row, exact horizontal coverage) into short runs of pixels with a coverage level, and those
 * runs are cached per size. Drawing is then only a loop of fills with no allocation, and no texture or font is
 * needed, so it also works on the loading screen before Minecraft has finished loading its assets.
 */
public final class Logo {
	/** Size of the artwork box in its own units. */
	private static final int SRC_W = 754, SRC_H = 471;
	private static final int LEVELS = 16;

	private static final class Shape {
		final int kind; // 0 = colour changes top to bottom, 1 = left to right
		final int c1, c2;
		final int[][] polys; // each polygon: x0,y0,x1,y1,... (even-odd fill)

		Shape(int kind, int c1, int c2, int[][] polys) {
			this.kind = kind;
			this.c1 = c1;
			this.c2 = c2;
			this.polys = polys;
		}
	}

	/** One shape rasterised for one size: runs of {row, x0, x1, level}. */
	private static final class Baked {
		int w, h;
		int[][] runs = new int[SHAPES.length][];
	}

	private static final Shape[] SHAPES = {
		new Shape(0, 0xFFFCFAFB, 0xFFD6D3DA, new int[][]{
			{100,100,176,171,479,171,487,175,508,196,511,209,509,214,479,244,471,248,369,248,316,298,494,467,614,467,443,312,444,310,510,310,524,305,606,225,611,216,612,199,607,188,527,106,510,99}}),
		new Shape(0, 0xFFF4F2F3, 0xFFD9D6DC, new int[][]{
			{355,210,242,210,164,287,164,397}}),
		new Shape(0, 0xFFFBB470, 0xFFF59A5C, new int[][]{
			{669,1,660,47,653,63,650,66,639,72,626,76,585,84,618,89,637,94,649,100,654,106,660,121,667,152,668,163,670,167,672,150,678,122,686,103,692,98,704,93,752,83,729,80,708,75,694,70,688,66,679,48}}),
		new Shape(1, 0xFFF79A5A, 0xFFF26A48, new int[][]{
			{610,37,572,40,525,50,461,72,429,88,458,88,489,73,525,59,562,47}}),
		new Shape(0, 0xFFFBB470, 0xFFF59A5C, new int[][]{
			{739,135,737,135,736,139,731,145,723,149,731,153,736,159,737,163,746,152,751,150,751,148,746,146,741,141}}),
		new Shape(1, 0xFFF47850, 0xFFF2573F, new int[][]{
			{724,186,693,219,644,258,579,299,509,335,528,352,532,352,577,327,617,301,649,277,683,246,705,220}}),
		new Shape(1, 0xFFF9B46C, 0xFFF2573F, new int[][]{
			{373,389,366,387,316,402,242,419,170,429,139,430,106,427,76,418,60,408,51,397,46,380,49,358,60,334,75,313,100,286,134,257,170,233,135,250,106,268,78,289,44,321,24,346,8,374,0,400,0,421,9,442,23,455,37,462,70,469,114,469,182,458,251,439,319,414,368,393}})
	};

	private static final Map<Integer, Baked> CACHE = new HashMap<>();

	private Logo() {}

	/** Width in pixels of the logo when it is h pixels tall. */
	public static int width(int h) {
		return Math.max(1, Math.round(h * (SRC_W / (float) SRC_H)));
	}

	private static Baked bake(int h) {
		Baked b = CACHE.get(h);
		if (b != null) return b;
		if (CACHE.size() > 12) CACHE.clear();
		b = new Baked();
		b.h = h;
		b.w = width(h);
		float s = h / (float) SRC_H;
		final int sub = 4;
		float[] cov = new float[b.w + 2];
		for (int si = 0; si < SHAPES.length; si++) {
			Shape sh = SHAPES[si];
			List<int[]> runs = new ArrayList<>();
			for (int row = 0; row < h; row++) {
				java.util.Arrays.fill(cov, 0f);
				boolean any = false;
				for (int k = 0; k < sub; k++) {
					float y = (row + (k + 0.5f) / sub) / s;
					float[] xs = new float[64];
					int n = 0;
					for (int[] p : sh.polys) {
						int cnt = p.length / 2;
						for (int i = 0; i < cnt; i++) {
							int j = (i + 1) % cnt;
							float y1 = p[i * 2 + 1], y2 = p[j * 2 + 1];
							if ((y1 <= y && y2 > y) || (y2 <= y && y1 > y)) {
								float t = (y - y1) / (y2 - y1);
								if (n < xs.length) xs[n++] = (p[i * 2] + t * (p[j * 2] - p[i * 2])) * s;
							}
						}
					}
					java.util.Arrays.sort(xs, 0, n);
					for (int i = 0; i + 1 < n; i += 2) {
						float xa = Math.max(0f, xs[i]), xb = Math.min(b.w, xs[i + 1]);
						if (xb <= xa) continue;
						any = true;
						int ia = (int) xa, ib = (int) Math.min(b.w - 0.0001f, xb);
						for (int x = ia; x <= ib; x++) {
							float l = Math.max(xa, x), r = Math.min(xb, x + 1);
							if (r > l) cov[x] += (r - l) / sub;
						}
					}
				}
				if (!any) continue;
				int x = 0;
				while (x < b.w) {
					int q = Math.round(Math.min(1f, cov[x]) * LEVELS);
					if (q <= 0) {
						x++;
						continue;
					}
					int x0 = x;
					while (x < b.w && Math.round(Math.min(1f, cov[x]) * LEVELS) == q) x++;
					// horizontal gradients: cut long runs so the colour changes smoothly along them
					int step = sh.kind == 1 ? Math.max(4, h / 14) : 100000;
					for (int a = x0; a < x; a += step) runs.add(new int[]{row, a, Math.min(x, a + step), q});
				}
			}
			int[] flat = new int[runs.size() * 4];
			for (int i = 0; i < runs.size(); i++) System.arraycopy(runs.get(i), 0, flat, i * 4, 4);
			b.runs[si] = flat;
		}
		CACHE.put(h, b);
		return b;
	}

	private static int lerp(int a, int b, float t) {
		return Theme.mix(a, b, t);
	}

	/**
	 * Draws the logo h units tall with its top-left corner at (x, y). alpha is 0..1.
	 *
	 * One layout unit is several real screen pixels (GUI scale times the menu scale), so rasterising at one run per unit
	 * makes every soft edge pixel a visible block and the logo looks blurry. It is rasterised at 3 to 4 times that
	 * resolution instead and drawn scaled back down, so its edges are anti-aliased at about real-pixel size.
	 */
	public static void draw(DrawContext ctx, int x, int y, int h, float alpha) {
		if (h < 6 || alpha <= 0.01f) return;
		int f = h <= 20 ? 4 : h <= 40 ? 3 : 2;
		Baked b = bake(h * f);
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate((float) x, (float) y);
		ctx.getMatrices().scale(1f / f, 1f / f);
		for (int si = 0; si < SHAPES.length; si++) {
			Shape sh = SHAPES[si];
			int[] r = b.runs[si];
			for (int i = 0; i < r.length; i += 4) {
				float t = sh.kind == 0 ? (r[i] + 0.5f) / b.h : (r[i + 1] + r[i + 2]) * 0.5f / b.w;
				int c = lerp(sh.c1, sh.c2, t);
				int a = Math.round(255f * alpha * r[i + 3] / LEVELS);
				if (a <= 0) continue;
				ctx.fill(r[i + 1], r[i], r[i + 2], r[i] + 1, (a << 24) | (c & 0xFFFFFF));
			}
		}
		ctx.getMatrices().popMatrix();
	}

	/** Same, centred on cx. */
	public static void drawCentered(DrawContext ctx, int cx, int y, int h, float alpha) {
		draw(ctx, cx - width(h) / 2, y, h, alpha);
	}
}
