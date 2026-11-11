package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Small immediate-mode UI toolkit shared by every Ray screen.
 *
 * Mouse input is polled through GLFW (left + right button) so nothing depends on the Screen click signatures,
 * which changed between Minecraft versions. Widgets are drawn and registered as "hits" every frame; call
 * {@link #poll()} at the end of the frame to dispatch clicks and slider drags.
 *
 * Extras over a plain toolkit: click ripples, press feedback, a modal mode (an open dialog blocks everything under it),
 * letter-spaced headings and a handful of crisp pixel icons.
 */
public final class Ui {
	public static final int H = 24;
	public static final int ROW = 28;

	private record Hit(String id, int x, int y, int w, int h, Runnable click, Runnable rclick, IntConsumer drag) {
		boolean contains(int px, int py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	private record Ripple(int cx, int cy, int x, int y, int w, int h, long start) {}

	public DrawContext ctx;
	public TextRenderer tr;
	public MinecraftClient mc = MinecraftClient.getInstance();
	public int mx, my;
	/** Button state as of the last {@link #poll()}. */
	public boolean left, right, leftPressed, rightPressed;

	/** Seconds since the previous frame (for animations). */
	public float dt = 0.016f;
	private long lastNanos = 0L;
	private final java.util.Map<String, Float> anims = new java.util.HashMap<>();

	private boolean leftWas = true, rightWas = true;
	private String dragId = null;
	private final List<Hit> hits = new ArrayList<>();
	private final List<Ripple> ripples = new ArrayList<>();
	private boolean clipped = false;
	private int clipX1, clipY1, clipX2, clipY2;
	/** True while a dialog is open: everything drawn outside {@link #modal(boolean)} ignores the mouse. */
	private boolean blocked = false, inModal = false;

	// ---------- frame lifecycle ----------

	/** Call from Screen#init so the click that opened the screen is not re-used. */
	public void reset() {
		leftWas = true;
		rightWas = true;
		dragId = null;
		left = right = leftPressed = rightPressed = false;
		ripples.clear();
	}

	public void begin(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
		this.ctx = ctx;
		this.tr = tr;
		this.mx = mouseX;
		this.my = mouseY;
		long now = System.nanoTime();
		if (lastNanos == 0L) {
			dt = 0.016f;
			lastNanos = now;
		} else if (now - lastNanos > 1_500_000L) {
			// begin() can be called many times in one frame (styled vanilla buttons): only a real new frame moves the clock
			dt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
			lastNanos = now;
		}
		hits.clear();
		clipped = false;
		blocked = UpdateDialog.isOpen();
		inModal = false;
	}

	/** Marks the widgets drawn next as belonging to a dialog on top (they work even though the screen under it is blocked). */
	public void modal(boolean on) {
		inModal = on;
	}

	/** Reads the mouse and dispatches clicks / drags to the widgets drawn this frame. */
	public void poll() {
		long win = mc.getWindow().getHandle();
		left = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		right = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		leftPressed = left && !leftWas;
		rightPressed = right && !rightWas;
		leftWas = left;
		rightWas = right;

		drawRipples();

		if (leftPressed) {
			for (Hit h : hits) {
				if (!h.contains(mx, my)) continue;
				if (h.click() == null && h.drag() == null) continue;
				if (h.click() != null) {
					addRipple(h);
					h.click().run();
					return; // the click may have opened another screen
				}
				dragId = h.id(); // drag-only widget (slider): applied just below
				break;
			}
		}
		if (rightPressed) {
			for (Hit h : hits) {
				if (h.contains(mx, my) && h.rclick() != null) {
					h.rclick().run();
					return;
				}
			}
		}
		if (left && dragId != null) {
			for (Hit h : hits) {
				if (h.id().equals(dragId) && h.drag() != null) {
					h.drag().accept(mx);
					break;
				}
			}
		}
		if (!left && dragId != null) {
			dragId = null;
			RayConfig.save();
		}
	}

	// ---------- click ripples ----------

	private void addRipple(Hit h) {
		if (!RayConfig.get().uiAnimations) return;
		if (h.w() * h.h() > 90000) return; // huge hit areas (whole pages) do not ripple
		while (ripples.size() > 6) ripples.remove(0);
		ripples.add(new Ripple(mx, my, h.x(), h.y(), h.w(), h.h(), System.currentTimeMillis()));
	}

	private void drawRipples() {
		if (ripples.isEmpty()) return;
		long now = System.currentTimeMillis();
		ripples.removeIf(r -> now - r.start() > 520L);
		for (Ripple r : ripples) {
			float t = (now - r.start()) / 520.0f;
			float e = Theme.easeOutCubic(t);
			int rad = Math.min(60, Math.max(6, Math.round(e * Math.max(r.w(), r.h()) * 0.9f) / 6 * 6));
			ctx.enableScissor(r.x(), r.y(), r.x() + r.w(), r.y() + r.h());
			circle(r.cx(), r.cy(), rad, Theme.fade(0xFFFFFFFF, 0.16f * (1f - t) * (1f - t)));
			ctx.disableScissor();
		}
	}

	// ---------- clipping (scrolling areas) ----------

	public void pushClip(int x, int y, int w, int h) {
		ctx.enableScissor(x, y, x + w, y + h);
		clipped = true;
		clipX1 = x;
		clipY1 = y;
		clipX2 = x + w;
		clipY2 = y + h;
	}

	public void popClip() {
		ctx.disableScissor();
		clipped = false;
	}

	// ---------- hit registration ----------

	public void hit(String id, int x, int y, int w, int h, Runnable click, Runnable rclick, IntConsumer drag) {
		if (blocked && !inModal) return;
		int x1 = x, y1 = y, x2 = x + w, y2 = y + h;
		if (clipped) {
			x1 = Math.max(x1, clipX1);
			y1 = Math.max(y1, clipY1);
			x2 = Math.min(x2, clipX2);
			y2 = Math.min(y2, clipY2);
		}
		if (x2 <= x1 || y2 <= y1) return;
		hits.add(new Hit(id, x1, y1, x2 - x1, y2 - y1, click, rclick, drag));
	}

	/** Left-click hit that also saves the config afterwards. */
	public void click(String id, int x, int y, int w, int h, Runnable action) {
		hit(id, x, y, w, h, () -> {
			action.run();
			RayConfig.save();
		}, null, null);
	}

	public boolean over(int x, int y, int w, int h) {
		if (blocked && !inModal) return false;
		if (clipped && (mx < clipX1 || mx >= clipX2 || my < clipY1 || my >= clipY2)) return false;
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** True while the left button is held down over the area (press feedback). */
	public boolean held(int x, int y, int w, int h) {
		return left && over(x, y, w, h);
	}

	// ---------- drawing primitives ----------

	public void rect(int x, int y, int w, int h, int c) {
		ctx.fill(x, y, x + w, y + h, c);
	}

	// ---- rounded shapes ----
	// Every ctx.fill is a separate draw element in Minecraft 1.21.x, so these shapes are built from as few fills as
	// possible: rows of a corner that share the same inset are merged into one rectangle, and the soft (anti-aliased)
	// edge pixels are only drawn for large radii, where they are visible.

	private static float clamp01(float v) {
		return v < 0f ? 0f : Math.min(1f, v);
	}

	private static int scaleAlpha(int argb, float f) {
		int a = (int) (((argb >>> 24) & 0xFF) * f);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** The shape of one corner for a radius: merged runs of fully covered pixels, single soft edge pixels, and a ring outline. */
	private static final class Corner {
		final int r;
		/** Rows/columns covered by the corner (the straight edges start here). Small radii use a cut corner: far fewer fills. */
		final int edge;
		/** {firstRow, endRow, inset}: rows [firstRow, endRow) are fully covered starting inset pixels from the left edge. */
		final int[][] runs;
		/** Soft edge pixels {row, column, coverage 0..255}: true anti-aliasing for radii of 7 and up. */
		final int[][] soft;
		/** Outline runs: {row, col0, col1, alpha0to255}. */
		final int[][] ring;

		Corner(int r) {
			this.r = r;
			if (r <= 6) {
				// small radius: a 1-2 pixel cut corner (octagon). Crisp, and only a handful of fills.
				int k = Math.max(2, r / 2);
				edge = k;
				runs = new int[k][];
				for (int i = 0; i < k; i++) runs[i] = new int[]{i, i + 1, k - i};
				soft = new int[0][];
				ring = new int[k - 1][];
				for (int i = 1; i < k; i++) ring[i - 1] = new int[]{i, k - i, k - i, 255};
				return;
			}
			edge = r;
			float[][] cov = new float[r][r];
			for (int i = 0; i < r; i++) {
				for (int j = 0; j < r; j++) {
					double d = Math.hypot(r - (j + 0.5), r - (i + 0.5));
					cov[i][j] = clamp01((float) (r - d + 0.5));
				}
			}
			// per row: the first fully covered column, and the soft pixels before it
			int[] full = new int[r];
			java.util.List<int[]> softList = new java.util.ArrayList<>();
			for (int i = 0; i < r; i++) {
				int j = 0;
				while (j < r - 1 && cov[i][j] < 0.72f) j++;
				full[i] = j;
				for (int k = 0; k < j; k++) {
					if (cov[i][k] >= 0.22f) softList.add(new int[]{i, k, Math.round(cov[i][k] * 255f)});
				}
			}
			soft = softList.toArray(new int[0][]);
			java.util.List<int[]> rl = new java.util.ArrayList<>();
			int start = 0;
			for (int i = 1; i <= r; i++) {
				if (i == r || full[i] != full[start]) {
					rl.add(new int[]{start, i, full[start]});
					start = i;
				}
			}
			runs = rl.toArray(new int[0][]);
			// outline: the cells of the 1px band along the edge, merged along each row
			java.util.List<int[]> ringList = new java.util.ArrayList<>();
			for (int i = 0; i < r; i++) {
				int s0 = -1;
				float mx = 0f;
				for (int j = 0; j <= r; j++) {
					float v = 0f;
					if (j < r) {
						double d = Math.hypot(r - (j + 0.5), r - (i + 0.5));
						v = Math.max(0f, clamp01((float) (r - d + 0.5)) - clamp01((float) (r - 1 - d + 0.5)));
					}
					boolean lit = v >= 0.30f;
					if (lit) {
						if (s0 < 0) {
							s0 = j;
							mx = 0f;
						}
						mx = Math.max(mx, v);
					}
					if (!lit && s0 >= 0) {
						ringList.add(new int[]{i, s0, j - 1, Math.round(255f * Math.min(1f, mx * 1.15f))});
						s0 = -1;
					}
				}
			}
			ring = ringList.toArray(new int[0][]);
		}
	}

	private static final java.util.Map<Integer, Corner> CORNERS = new java.util.HashMap<>();

	private static Corner corner(int r) {
		return CORNERS.computeIfAbsent(r, Corner::new);
	}

	/** Filled rounded rectangle. */
	public void rrect(int x, int y, int w, int h, int r, int c) {
		rrectGrad(x, y, w, h, r, c, c);
	}

	/**
	 * Rounded rectangle with a vertical gradient from c1 (top) to c2 (bottom). Corner rows are coloured by their own
	 * position, so the gradient is smooth even on shapes that are all corner (switches, capsules).
	 */
	public void rrectGrad(int x, int y, int w, int h, int r, int c1, int c2) {
		if (w <= 0 || h <= 0) return;
		r = Math.min(r, Math.min(w, h) / 2);
		boolean flat = c1 == c2;
		if (r <= 1) {
			if (flat) ctx.fill(x, y, x + w, y + h, c1);
			else ctx.fillGradient(x, y, x + w, y + h, c1, c2);
			return;
		}
		Corner cn = corner(r);
		int e = cn.edge;
		if (h - 2 * e > 0) {
			if (flat) ctx.fill(x, y + e, x + w, y + h - e, c1);
			else ctx.fillGradient(x, y + e, x + w, y + h - e, Theme.mix(c1, c2, e / (float) h), Theme.mix(c1, c2, (h - e) / (float) h));
		}
		for (int[] run : cn.runs) {
			int ins = run[2];
			float tm = (run[0] + run[1]) * 0.5f / h;
			int topCol = flat ? c1 : Theme.mix(c1, c2, tm);
			int botCol = flat ? c1 : Theme.mix(c1, c2, 1f - tm);
			ctx.fill(x + ins, y + run[0], x + w - ins, y + run[1], topCol);
			ctx.fill(x + ins, y + h - run[1], x + w - ins, y + h - run[0], botCol);
		}
		for (int[] sp : cn.soft) {
			int i = sp[0], j = sp[1];
			float cv = sp[2] / 255f;
			float tt = (i + 0.5f) / h;
			int ct = scaleAlpha(flat ? c1 : Theme.mix(c1, c2, tt), cv), cb = scaleAlpha(flat ? c1 : Theme.mix(c1, c2, 1f - tt), cv);
			ctx.fill(x + j, y + i, x + j + 1, y + i + 1, ct);
			ctx.fill(x + w - 1 - j, y + i, x + w - j, y + i + 1, ct);
			ctx.fill(x + j, y + h - 1 - i, x + j + 1, y + h - i, cb);
			ctx.fill(x + w - 1 - j, y + h - 1 - i, x + w - j, y + h - i, cb);
		}
	}

	/** A 1px outline that follows the same rounded shape as {@link #rrect}. Works with translucent colours. */
	public void ring(int x, int y, int w, int h, int r, int c) {
		if (w <= 1 || h <= 1) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 1) {
			border(x, y, w, h, c);
			return;
		}
		Corner cn = corner(r);
		int e = cn.edge;
		ctx.fill(x + e, y, x + w - e, y + 1, c);
		ctx.fill(x + e, y + h - 1, x + w - e, y + h, c);
		ctx.fill(x, y + e, x + 1, y + h - e, c);
		ctx.fill(x + w - 1, y + e, x + w, y + h - e, c);
		for (int[] run : cn.ring) {
			int i = run[0], j0 = run[1], j1 = run[2];
			int cc = run[3] >= 250 ? c : scaleAlpha(c, run[3] / 255f);
			ctx.fill(x + j0, y + i, x + j1 + 1, y + i + 1, cc);
			ctx.fill(x + w - 1 - j1, y + i, x + w - j0, y + i + 1, cc);
			ctx.fill(x + j0, y + h - 1 - i, x + j1 + 1, y + h - i, cc);
			ctx.fill(x + w - 1 - j1, y + h - 1 - i, x + w - j0, y + h - i, cc);
		}
	}

	/** Filled circle. */
	public void circle(int cx, int cy, int r, int c) {
		rrect(cx - r, cy - r, r * 2, r * 2, r, c);
	}

	/** Rounded panel with a 1px outline. The fill may be translucent (glass). */
	public void panel(int x, int y, int w, int h, int r, int fill, int stroke) {
		rrect(x, y, w, h, r, fill);
		rimGlass(x, y, w, h, r, 0.22f, 0.07f);
		ring(x, y, w, h, r, Theme.fade(stroke, 0.35f));
	}

	/** Rounded panel with a vertical gradient fill and a 1px outline. */
	public void panelGrad(int x, int y, int w, int h, int r, int top, int bottom, int stroke) {
		rrectGrad(x, y, w, h, r, top, bottom);
		int rim = (stroke >>> 24) / 255f > 0.02f ? 1 : 0;
		float sa = (stroke >>> 24) / 255f;
		rimGlass(x, y, w, h, r, 0.30f * Math.min(1f, sa * 2f), 0.08f * Math.min(1f, sa * 2f));
		if (rim == 1) ring(x, y, w, h, r, Theme.fade(stroke, 0.30f));
		if (w > 2 * r + 8) specular(x + r, y + 1, w - 2 * r, 0.20f * Math.min(1f, sa * 2f));
	}

	/**
	 * Soft drop shadow: two translucent layers that grow outward. The layers are only a few percent opaque, so their
	 * corners are cut (a handful of fills) instead of smoothly rounded (dozens of fills): you cannot tell the difference.
	 */
	public void shadow(int x, int y, int w, int h, int r) {
		if (!RayConfig.get().uiShadows) return;
		rrect(x - 8, y - 4, w + 16, h + 16, 6, Theme.fade(0xFF000000, 0.05f));
	}

	/** A coloured halo around a shape (hovered or enabled controls, focused fields). strength is 0..1. Cheap, see {@link #shadow}. */
	public void glow(int x, int y, int w, int h, int r, int color, float strength) {
		// intentionally empty: coloured halos were removed from the design (kept so existing callers still compile)
	}

	/** The window colour made see-through according to the Glass setting, so the scene behind it shows. */
	public int glass(int opaque) {
		return Theme.fade(opaque, 0.18f + 0.55f * RayConfig.get().uiGlass / 100.0f);
	}

	// ---------- liquid glass ----------

	static int white(float a) {
		return Theme.fade(0xFFFFFFFF, Math.max(0f, Math.min(1f, a)));
	}

	/**
	 * The rim of a glass surface: bright along the top-left where light would catch it, fading to a faint line along the
	 * bottom-right. hi and lo are the white opacities (0..1) of the two ends.
	 */
	public void rimGlass(int x, int y, int w, int h, int r, float hi, float lo) {
		rimGlass(x, y, w, h, r, hi, lo, 0xFFFFFFFF);
	}

	/** Same, with the light tinted (an accent rim for an enabled control). */
	public void rimGlass(int x, int y, int w, int h, int r, float hi, float lo, int tint) {
		if (w <= 1 || h <= 1) return;
		r = Math.min(r, Math.min(w, h) / 2);
		int cTL = Theme.fade(tint, Math.min(1f, hi)), cTR = Theme.fade(tint, Math.min(1f, hi * 0.5f + lo * 0.5f)), cBL = Theme.fade(tint, Math.min(1f, lo * 1.3f)), cBR = Theme.fade(tint, Math.min(1f, lo));
		int e = r <= 1 ? 0 : corner(r).edge;
		int x1 = x + e, x2 = x + w - e;
		final int segs = 4;
		for (int i = 0; i < segs; i++) {
			int a = x1 + (x2 - x1) * i / segs, b = x1 + (x2 - x1) * (i + 1) / segs;
			ctx.fill(a, y, b, y + 1, Theme.mix(cTL, cTR, (i + 0.5f) / segs));
			ctx.fill(a, y + h - 1, b, y + h, Theme.mix(cBL, cBR, (i + 0.5f) / segs));
		}
		ctx.fillGradient(x, y + e, x + 1, y + h - e, cTL, cBL);
		ctx.fillGradient(x + w - 1, y + e, x + w, y + h - e, cTR, cBR);
		if (r <= 1) return;
		for (int[] run : corner(r).ring) {
			int i = run[0], j0 = run[1], j1 = run[2];
			float q = run[3] >= 250 ? 1f : run[3] / 255f;
			ctx.fill(x + j0, y + i, x + j1 + 1, y + i + 1, Theme.fade(cTL, q));
			ctx.fill(x + w - 1 - j1, y + i, x + w - j0, y + i + 1, Theme.fade(cTR, q));
			ctx.fill(x + j0, y + h - 1 - i, x + j1 + 1, y + h - i, Theme.fade(cBL, q));
			ctx.fill(x + w - 1 - j1, y + h - 1 - i, x + w - j0, y + h - i, Theme.fade(cBR, q));
		}
	}

	/** A 1px white highlight that is brightest in the middle and fades toward both ends (light on the glass edge). */
	public void specular(int x, int y, int w, float peak) {
		if (w <= 8) return;
		final int segs = 8;
		for (int i = 0; i < segs; i++) {
			int x1 = x + w * i / segs, x2 = x + w * (i + 1) / segs;
			double a = Math.sin(((i + 0.5) / segs) * Math.PI);
			ctx.fill(x1, y, x2, y + 1, white((float) (peak * a * a)));
		}
	}

	/**
	 * A glass pane: tinted body, a soft sheen over the upper part, a light-catching rim and a specular edge.
	 * top and bottom are the body colours (usually translucent).
	 */
	public void glassPanel(int x, int y, int w, int h, int r, int top, int bottom, float rimHi, float rimLo) {
		if (w <= 2 || h <= 2) return;
		r = Math.min(r, Math.min(w, h) / 2);
		rrectGrad(x, y, w, h, r, top, bottom);
		rimGlass(x, y, w, h, r, rimHi * 0.7f, rimLo);
	}

	/** A floating glass window: soft shadow, the pane, and a faint warm bounce light along the bottom edge. */
	public void surface(int x, int y, int w, int h, int r) {
		r = Math.min(r, 14);
		shadow(x, y, w, h, r);
		glassPanel(x, y, w, h, r, glass(0xFF1B1B21), glass(0xFF0C0C10), 0.22f, 0.08f);
	}

	/** A recessed field or row inside a window: a faint lighter tint and a hairline. hover is 0..1. */
	public void inset(int x, int y, int w, int h, int r, float hover) {
		r = Math.min(r, 6); // controls use cut corners everywhere
		rrect(x, y, w, h, r, white(0.05f + 0.045f * hover));
		// borderless like a settings list; the hairline only appears under the cursor (saves a lot of draw calls)
		if (hover > 0.05f) ring(x, y, w, h, r, Theme.mix(white(0.12f), Theme.fade(Theme.accent(), 0.60f), hover * 0.7f));
	}

	/** A 1px line that is brightest in the middle and fades toward both ends, in any colour. */
	public void accentLine(int x, int y, int w, int color) {
		if (w <= 8) return;
		final int segs = 10;
		for (int i = 0; i < segs; i++) {
			int x1 = x + w * i / segs, x2 = x + w * (i + 1) / segs;
			double a = Math.sin(((i + 0.5) / segs) * Math.PI);
			ctx.fill(x1, y, x2, y + 1, Theme.fade(color, (float) (0.80 * a * a)));
		}
	}

	/** A horizontal gradient bar (c1 on the left, c2 on the right) with round ends. */
	public void gradBar(int x, int y, int w, int h, int c1, int c2) {
		if (w <= 0 || h <= 0) return;
		rrect(x, y, w, h, h / 2, c1); // flat on purpose: gradients on every bar read as generated
	}

	/** Text colour that reads on top of the accent colour (white, or near-black on a light accent such as Ice). */
	public static int onAccent() {
		int c = Theme.accent();
		float l = (0.299f * ((c >> 16) & 0xFF) + 0.587f * ((c >> 8) & 0xFF) + 0.114f * (c & 0xFF)) / 255f;
		return l > 0.78f ? 0xFF121216 : 0xFFFFFFFF;
	}

	/** A solid accent surface (primary buttons, selected states): accent to accent2 with a bright top edge. */
	public void accentFill(int x, int y, int w, int h, int r, float lift) {
		rrect(x, y, w, h, r, Theme.mix(Theme.accent(), 0xFFFFFFFF, 0.10f * lift));
	}

	/** A status dot with a soft halo. */
	public void dot(int cx, int cy, int r, int color, boolean halo) {
		if (halo) circle(cx, cy, r + 3, Theme.fade(color, 0.16f));
		circle(cx, cy, r, color);
	}

	/** A rounded plate behind an icon: a glass tile with a faint tint (strength 0..1 = how strongly). */
	public void plate(int x, int y, int size, int tint, float strength) {
		rrect(x, y, size, size, 6, white(0.06f + 0.04f * strength));
	}

	// ---------- animation ----------

	public float anim(String id, boolean target) {
		return animTo(id, target ? 1.0f : 0.0f);
	}

	/** Eases a stored value toward target (instantly when animations are off). First use starts at the target. */
	public float animTo(String id, float target) {
		if (!RayConfig.get().uiAnimations) {
			anims.put(id, target);
			return target;
		}
		Float cur = anims.get(id);
		if (cur == null) {
			anims.put(id, target);
			return target;
		}
		float v = cur + (target - cur) * (1.0f - (float) Math.exp(-dt * 16.0f));
		if (Math.abs(target - v) < 0.002f) v = target;
		anims.put(id, v);
		return v;
	}

	private final java.util.Map<String, Long> appearAt = new java.util.HashMap<>();

	/** 0..1 entrance progress (ease-out) for an element that starts appearing delayMs after it is first drawn. */
	public float appear(String id, int delayMs) {
		if (!RayConfig.get().uiAnimations) return 1.0f;
		long now = System.currentTimeMillis();
		Long start = appearAt.get(id);
		if (start == null) {
			start = now + delayMs;
			appearAt.put(id, start);
		}
		return Theme.easeOutCubic((now - start) / 320.0f);
	}

	/** Forget every entrance so the next frame plays them again (filter changed, page changed). */
	public void resetAppear() {
		appearAt.clear();
	}

	public void border(int x, int y, int w, int h, int c) {
		ctx.fill(x, y, x + w, y + 1, c);
		ctx.fill(x, y + h - 1, x + w, y + h, c);
		ctx.fill(x, y, x + 1, y + h, c);
		ctx.fill(x + w - 1, y, x + w, y + h, c);
	}

	// ---------- pixel icons ----------

	/** Draws a 1-bit pixel picture ('#' = lit) with the given pixel size. */
	public void pix(String[] rows, int x, int y, int size, int c) {
		for (int r = 0; r < rows.length; r++) {
			String row = rows[r];
			int start = -1;
			for (int col = 0; col <= row.length(); col++) {
				boolean lit = col < row.length() && row.charAt(col) == '#';
				if (lit && start < 0) start = col;
				if (!lit && start >= 0) {
					ctx.fill(x + start * size, y + r * size, x + col * size, y + (r + 1) * size, c);
					start = -1;
				}
			}
		}
	}

	private static final String[] HEART = {".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."};
	private static final String[] CLOSE = {"#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"};
	private static final String[] BACK = {"...#", "..##", ".###", "####", ".###", "..##", "...#"};
	private static final String[] GEAR = {"#.###.#", ".#####.", "###.###", "##...##", "###.###", ".#####.", "#.###.#"};
	private static final String[] CHECK = {"......#", ".....##", "#...##.", "##.##..", ".###...", "..#...."};
	private static final String[] CHEV = {"#..", "##.", "###", "##.", "#.."};
	private static final String[] ARROW_UP = {"..#..", ".###.", "#.#.#", "..#..", "..#.."};
	private static final String[] PENCIL = {"....##.", "...####", "..###..", ".###...", "###....", "##.....", "#......"};

	public void heart(int x, int y, int c) {
		pix(HEART, x, y, 1, c);
	}

	public void closeIcon(int x, int y, int c) {
		pix(CLOSE, x, y, 1, c);
	}

	public void backIcon(int x, int y, int c) {
		pix(BACK, x, y, 1, c);
	}

	/** Cog glyph, 7x7. */
	public void gearIcon(int x, int y, int c) {
		pix(GEAR, x, y, 1, c);
	}

	/** Tick mark, 7x6. */
	public void checkIcon(int x, int y, int c) {
		pix(CHECK, x, y, 1, c);
	}

	/** Small right-pointing arrow, 3x5. */
	public void chevron(int x, int y, int c) {
		pix(CHEV, x, y, 1, c);
	}

	public void arrowUpIcon(int x, int y, int c) {
		pix(ARROW_UP, x, y, 1, c);
	}

	public void pencilIcon(int x, int y, int c) {
		pix(PENCIL, x, y, 1, c);
	}

	/** Magnifier, 9x9. */
	public void searchIcon(int x, int y, int c) {
		ring(x, y, 7, 7, 3, c);
		ctx.fill(x + 6, y + 6, x + 7, y + 7, c);
		ctx.fill(x + 7, y + 7, x + 8, y + 8, c);
		ctx.fill(x + 8, y + 8, x + 9, y + 9, c);
	}

	/** Four dots in a square (grid view), 8x8. */
	public void dotsIcon(int x, int y, int c) {
		rrect(x, y, 3, 3, 1, c);
		rrect(x + 5, y, 3, 3, 1, c);
		rrect(x, y + 5, 3, 3, 1, c);
		rrect(x + 5, y + 5, 3, 3, 1, c);
	}

	/** Three bars (list view), 9x7. */
	public void listIcon(int x, int y, int c) {
		ctx.fill(x, y, x + 9, y + 1, c);
		ctx.fill(x, y + 3, x + 9, y + 4, c);
		ctx.fill(x, y + 6, x + 9, y + 7, c);
	}

	/** Small "sliders" glyph used for the settings button. */
	public void slidersIcon(int x, int y, int c) {
		ctx.fill(x, y + 1, x + 10, y + 2, c);
		ctx.fill(x, y + 4, x + 10, y + 5, c);
		ctx.fill(x, y + 7, x + 10, y + 8, c);
		ctx.fill(x + 6, y, x + 8, y + 3, c);
		ctx.fill(x + 2, y + 3, x + 4, y + 6, c);
		ctx.fill(x + 5, y + 6, x + 7, y + 9, c);
	}

	// ---------- text ----------

	private static final String[] CH = new String[128];

	static {
		for (int i = 0; i < CH.length; i++) CH[i] = String.valueOf((char) i);
	}

	private static String ch(char c) {
		return c < 128 ? CH[c] : String.valueOf(c);
	}

	public int w(String s) {
		return ClientFont.width(tr, s);
	}

	public void text(String s, int x, int y, int c) {
		ClientFont.draw(ctx, tr, s, x, y, c, false);
	}

	public void textShadow(String s, int x, int y, int c) {
		ClientFont.draw(ctx, tr, s, x, y, c, true);
	}

	public void center(String s, int cx, int y, int c) {
		ClientFont.draw(ctx, tr, s, cx - ClientFont.width(tr, s) / 2, y, c, false);
	}

	public void right(String s, int rx, int y, int c) {
		ClientFont.draw(ctx, tr, s, rx - ClientFont.width(tr, s), y, c, false);
	}

	/** Heading text: the bold face of the client font. */
	public void headText(String s, int x, int y, int c) {
		ClientFont.drawHead(ctx, tr, s, x, y, c, false);
	}

	public int headWidth(String s) {
		return ClientFont.widthHead(tr, s);
	}

	public void headCenter(String s, int cx, int y, int c) {
		ClientFont.drawHead(ctx, tr, s, cx - ClientFont.widthHead(tr, s) / 2, y, c, false);
	}

	public void scaledHead(String s, int x, int y, float scale, int c) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate((float) x, (float) y);
		ctx.getMatrices().scale(scale, scale);
		ClientFont.drawHead(ctx, tr, s, 0, 0, c, false);
		ctx.getMatrices().popMatrix();
	}

	/** Width of text drawn with {@link #spaced} (sp extra pixels between letters). */
	public int spacedWidth(String s, int sp) {
		if (sp <= 1) return ClientFont.width(tr, s);
		int w = 0;
		for (int i = 0; i < s.length(); i++) w += ClientFont.width(tr, ch(s.charAt(i))) + sp;
		return Math.max(0, w - sp);
	}

	/**
	 * Letter-spaced text, used for small headings. Spacing of 1 or less is drawn as one plain string (the font already
	 * has good spacing, and it is one draw call instead of one per letter); wider spacing is drawn letter by letter.
	 */
	public void spaced(String s, int x, int y, int c, int sp) {
		if (sp <= 1) {
			ClientFont.draw(ctx, tr, s, x, y, c, false);
			return;
		}
		int cx = x;
		for (int i = 0; i < s.length(); i++) {
			String g = ch(s.charAt(i));
			if (s.charAt(i) != ' ') ClientFont.draw(ctx, tr, g, cx, y, c, false);
			cx += ClientFont.width(tr, g) + sp;
		}
	}

	/** Letter-spaced text with a dark outline (used for the RAY CLIENT name). */
	public void spacedOutlinedCenter(String s, int cx, int y, int c, int sp) {
		int x = cx - spacedWidth(s, sp) / 2;
		int oc = Theme.fade(0xFF000000, ((c >>> 24) / 255f) * 0.85f);
		spaced(s, x - 1, y, oc, sp);
		spaced(s, x + 1, y, oc, sp);
		spaced(s, x, y - 1, oc, sp);
		spaced(s, x, y + 1, oc, sp);
		spaced(s, x, y, c, sp);
	}

	public void spacedCenter(String s, int cx, int y, int c, int sp) {
		spaced(s, cx - spacedWidth(s, sp) / 2, y, c, sp);
	}

	public String fit(String s, int maxW) {
		if (ClientFont.width(tr, s) <= maxW) return s;
		String e = "...";
		while (s.length() > 1 && ClientFont.width(tr, s + e) > maxW) s = s.substring(0, s.length() - 1);
		return s + e;
	}

	public void scaledText(String s, int x, int y, float scale, int c, boolean shadow) {
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate((float) x, (float) y);
		ctx.getMatrices().scale(scale, scale);
		ClientFont.draw(ctx, tr, s, 0, 0, c, shadow);
		ctx.getMatrices().popMatrix();
	}

	/** A small pill label such as NEW. Returns its width. */
	public int badge(int x, int y, String label, int fill, int textColor) {
		int w = ClientFont.width(tr, label) * 7 / 10 + 8;
		rrect(x, y, w, 9, 4, fill);
		scaledText(label, x + 4, y + 2, 0.7f, textColor, false);
		return w;
	}

	// ---------- small widgets ----------

	/** Filter chip (a capsule). Returns its width so callers can lay chips out in a row. */
	public int chip(int x, int y, String label, boolean active, Runnable click) {
		int w = ClientFont.width(tr, label) + 18;
		float a = anim("chip" + label, over(x, y, w, 16));
		float on = anim("chipon" + label, active);
		if (on > 0.02f) {
			rrect(x, y, w, 16, 5, Theme.fade(Theme.accent(), on));
		}
		if (on < 0.98f) {
			rrect(x, y, w, 16, 5, white((0.05f + 0.05f * a) * (1f - on)));
			ring(x, y, w, 16, 5, white((0.12f + 0.08f * a) * (1f - on)));
		}
		text(label, x + 9, y + 4, Theme.mix(Theme.mix(Theme.DIM, 0xFFFFFFFF, a * 0.7f), onAccent(), on));
		click("chip" + label, x, y, w, 16, click);
		return w;
	}

	/** Square icon button; draw the glyph yourself via the returned hover flag. */
	public boolean iconButton(int x, int y, int w, int h, int base, int hover, Runnable click) {
		boolean hov = over(x, y, w, h);
		float a = anim("ib" + x + "," + y, hov);
		float p = anim("ibp" + x + "," + y, held(x, y, w, h));
		rrect(x, y + Math.round(p), w, h, 5, Theme.mix(Theme.fade(base, 0.55f), Theme.fade(hover, 0.85f), a));
		ring(x, y + Math.round(p), w, h, 5, white(0.10f + 0.12f * a));
		click("ib" + x + "," + y, x, y, w, h, click);
		return hov;
	}

	/** A small tinted button (Load / Save / Delete...). color is the tint; it only shows as a faint wash and on hover. */
	public void labelButton(int x, int y, int w, int h, String label, int color, Runnable action) {
		boolean hov = over(x, y, w, h);
		float a = anim("lb" + label + x, hov);
		float p = anim("lbp" + label + x, held(x, y, w, h));
		int yy = y + Math.round(p);
		rrect(x, yy, w, h, 5, Theme.mix(white(0.07f), Theme.fade(color, 0.30f), 0.35f + 0.65f * a));
		center(label, x + w / 2, yy + (h - 8) / 2, Theme.mix(0xFFE4E4EC, 0xFFFFFFFF, a));
		click("lb" + label + x + "," + y, x, y, w, h, action);
	}

	/** A generic glass button; tint colours its hover state. enabled=false greys it out. */
	public void pill(int x, int y, int w, int h, String label, int tint, boolean enabled, Runnable action) {
		boolean hov = enabled && over(x, y, w, h);
		float a = anim("pl" + label + x + "," + y, hov);
		float p = anim("plp" + label + x + "," + y, enabled && held(x, y, w, h));
		int yy = y + Math.round(p);
		int r = 5;
		rrect(x, yy, w, h, r, enabled ? white(0.07f + 0.06f * a) : white(0.03f));
		if (enabled && a > 0.05f) ring(x, yy, w, h, r, white(0.16f * a));
		center(label, x + w / 2, yy + (h - 8) / 2, enabled ? Theme.mix(0xFFD8D8E2, 0xFFFFFFFF, a) : Theme.MUTE);
		if (enabled) click("pl" + label + x + "," + y, x, y, w, h, action);
	}

	/** The primary button: filled with the accent gradient. */
	public void primary(int x, int y, int w, int h, String label, Runnable action) {
		boolean hov = over(x, y, w, h);
		float a = anim("pr" + label + x + "," + y, hov);
		float p = anim("prp" + label + x + "," + y, held(x, y, w, h));
		int yy = y + Math.round(p);
		int r = 5;
		accentFill(x, yy, w, h, r, a);
		center(label, x + w / 2, yy + (h - 8) / 2, onAccent());
		click("pr" + label + x + "," + y, x, y, w, h, action);
	}

	public void scrollbar(int x, int y, int h, int viewH, int contentH, int scroll) {
		if (contentH <= viewH) return;
		boolean hov = over(x - 3, y, 9, h);
		float a = anim("sbar" + x + "," + y, hov);
		int w = 3 + Math.round(a);
		rrect(x, y, w, h, w / 2, white(0.06f));
		int th = Math.max(18, h * viewH / contentH);
		int ty = y + (int) ((long) (h - th) * scroll / Math.max(1, contentH - viewH));
		rrect(x, ty, w, th, w / 2, Theme.mix(white(0.30f), Theme.fade(Theme.accent(), 0.85f), a));
	}

	/** A progress bar with a soft moving shine on the filled part. frac is 0..1. */
	public void progress(int x, int y, int w, int h, float frac, int color) {
		rrect(x, y, w, h, h / 2, white(0.07f));
		ring(x, y, w, h, h / 2, white(0.10f));
		int fw = Math.round((w - 2) * Theme.clamp01(frac));
		if (fw < h) fw = frac <= 0.001f ? 0 : h;
		if (fw <= 0) return;
		rrect(x + 1, y + 1, fw, h - 2, (h - 2) / 2, color);
	}

	// ---------- settings rows (r = {x, y, width}) ----------

	private void card(int[] r, String id) {
		float a = anim("h" + id, over(r[0], r[1], r[2], H));
		inset(r[0], r[1], r[2], H, 8, a);
	}

	/** An iOS-style switch. k is 0..1 (animated). */
	public void switchControl(int tx, int ty, float k) {
		if (k < 0.98f) rrect(tx, ty, 34, 18, 9, white(0.13f * (1f - k)));
		if (k > 0.02f) rrect(tx, ty, 34, 18, 9, Theme.fade(Theme.accent(), k));
		int kx = tx + 9 + Math.round(k * 16f), ky = ty + 9;
		circle(kx, ky, 7, Theme.mix(0xFFCFCFD8, Ui.onAccent() == 0xFFFFFFFF ? 0xFFFFFFFF : 0xFF121216, k));
	}

	public void toggle(int[] r, String name, boolean on, Runnable flip) {
		card(r, "t" + name);
		float k = anim("k" + name, on);
		text(fit(name, r[2] - 64), r[0] + 11, r[1] + 8, Theme.mix(Theme.DIM, Theme.TEXT, 0.4f + 0.6f * k));
		switchControl(r[0] + r[2] - 44, r[1] + 3, k);
		click("t" + name, r[0], r[1], r[2], H, flip);
	}

	public void choice(int[] r, String name, String value, Runnable next) {
		card(r, "c" + name);
		String v = fit(value, r[2] / 2);
		text(fit(name, r[2] - w(v) - 30), r[0] + 11, r[1] + 8, Theme.TEXT);
		right(v, r[0] + r[2] - 18, r[1] + 8, Theme.accent());
		chevron(r[0] + r[2] - 11, r[1] + 9, Theme.MUTE);
		click("c" + name, r[0], r[1], r[2], H, next);
	}

	public void colorChoice(int[] r, String name, String valueName, int swatch, Runnable next) {
		card(r, "cc" + name);
		text(fit(name, r[2] - 80), r[0] + 11, r[1] + 8, Theme.TEXT);
		circle(r[0] + r[2] - 17, r[1] + 12, 7, swatch);
		ring(r[0] + r[2] - 24, r[1] + 5, 14, 14, 7, white(0.45f));
		right(valueName, r[0] + r[2] - 30, r[1] + 8, Theme.DIM);
		click("cc" + name, r[0], r[1], r[2], H, next);
	}

	/** Slider with a fully custom value label. */
	public void sliderShown(int[] r, String name, int min, int max, int value, String shown, IntConsumer set) {
		card(r, "s" + name);
		text(fit(name, r[2] - w(shown) - 24), r[0] + 11, r[1] + 4, Theme.TEXT);
		right(shown, r[0] + r[2] - 11, r[1] + 4, Theme.DIM);
		final int bx = r[0] + 11, bw = r[2] - 22, by = r[1] + 17;
		String id = "s" + name + "@" + r[0] + "," + r[1];
		boolean active = dragId != null && dragId.equals(id);
		float hv = anim("sk" + id, active || over(r[0], r[1] + 10, r[2], 14));
		rrect(bx, by, bw, 4, 2, white(0.12f));
		float target = (value - min) / (float) Math.max(1, max - min);
		float shownFill = animTo("sf" + id, Math.max(0f, Math.min(1f, target)));
		int fill = Math.round(shownFill * bw);
		rrect(bx, by, Math.max(4, fill), 4, 2, Theme.accent());
		int kr = 5 + Math.round(hv);
		circle(bx + fill, by + 2, kr, 0xFFFFFFFF);
		hit(id, r[0], r[1] + 10, r[2], 14, null, null, m -> {
			int nv = min + Math.round((m - bx) * (max - min) / (float) bw);
			set.accept(Math.max(min, Math.min(max, nv)));
		});
	}

	/** Slider whose label is the value followed by a suffix (e.g. "%"). */
	public void slider(int[] r, String name, int min, int max, int value, String suffix, IntConsumer set) {
		sliderShown(r, name, min, max, value, value + suffix, set);
	}

	public void slider(int[] r, String name, int min, int max, int value, IntConsumer set) {
		sliderShown(r, name, min, max, value, String.valueOf(value), set);
	}

	/** A full-width action button (glass; the accent shows on hover). */
	public void button(int[] r, String label, Runnable action) {
		boolean hov = over(r[0], r[1], r[2], H);
		float a = anim("b" + label, hov);
		float p = anim("bp" + label, held(r[0], r[1], r[2], H));
		int yy = r[1] + Math.round(p);
		rrect(r[0], yy, r[2], H, 5, white(0.07f + 0.06f * a));
		if (a > 0.05f) ring(r[0], yy, r[2], H, 5, white(0.16f * a));
		center(label, r[0] + r[2] / 2, yy + 8, Theme.mix(0xFFDADAE4, 0xFFFFFFFF, a));
		click("b" + label, r[0], r[1], r[2], H, action);
	}

	// ---------- colour wheel ----------

	private final java.util.Map<String, float[]> hsvState = new java.util.HashMap<>();
	private final int[] wheelColors = new int[42 * 42];
	private int wheelBrightness = -1;

	/**
	 * A colour wheel: drag around the disc to pick hue (angle) and saturation (distance from the centre), with a
	 * brightness slider, a preview and the hex code. rgb is 0xRRGGBB. Takes one tall block of the form.
	 */
	public void colorPicker(Form f, String id, int rgb, IntConsumer set) {
		final int R = 42, boxH = 94;
		int x = f.x(), w = f.width();
		int top = f.reserve(boxH + 6);
		inset(x, top, w, boxH, 8, 0f);

		float[] hsv = hsvState.computeIfAbsent(id, k -> {
			float[] a = new float[3];
			java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a);
			return a;
		});
		// the colour was changed from somewhere else (profile, reset...): follow it
		if ((java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF) != (rgb & 0xFFFFFF)) {
			java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsv);
		}

		final int cx = x + 8 + R, cy = top + 6 + R;
		// the disc is made of 2x2-pixel cells; the colours only get recomputed when the brightness changes
		int bq = Math.round(hsv[2] * 100f);
		boolean fresh = bq != wheelBrightness;
		if (fresh) {
			wheelBrightness = bq;
			java.util.Arrays.fill(wheelColors, 0);
		}
		for (int py = -R; py < R; py += 2) {
			for (int px = -R; px < R; px += 2) {
				double dx = px + 1, dy = py + 1;
				double r = Math.sqrt(dx * dx + dy * dy);
				if (r > R) continue;
				int idx = ((py + R) / 2) * 42 + ((px + R) / 2);
				if (wheelColors[idx] == 0) {
					float hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1.0) % 1.0);
					wheelColors[idx] = 0xFF000000 | java.awt.Color.HSBtoRGB(hue, (float) (r / R), hsv[2]);
				}
				ctx.fill(cx + px, cy + py, cx + px + 2, cy + py + 2, wheelColors[idx]);
			}
		}
		// marker
		double ang = hsv[0] * 2 * Math.PI;
		int mx2 = cx + (int) Math.round(Math.cos(ang) * hsv[1] * R), my2 = cy + (int) Math.round(Math.sin(ang) * hsv[1] * R);
		ring(mx2 - 5, my2 - 5, 11, 11, 5, 0xFF000000);
		ring(mx2 - 4, my2 - 4, 9, 9, 4, 0xFFFFFFFF);
		hit("wheel" + id, cx - R, cy - R, R * 2, R * 2, null, null, m -> {
			double dx = m - cx, dy = this.my - cy;
			hsv[0] = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1.0) % 1.0);
			hsv[1] = (float) Math.min(1.0, Math.sqrt(dx * dx + dy * dy) / R);
			set.accept(java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF);
		});

		int rx = x + 8 + R * 2 + 14, rw = w - (rx - x) - 8;
		rrect(rx, top + 6, rw, 20, 5, 0xFF000000 | (rgb & 0xFFFFFF));
		ring(rx, top + 6, rw, 20, 5, Theme.STROKE_HI);
		text(String.format("#%06X", rgb & 0xFFFFFF), rx, top + 32, Theme.DIM);
		sliderShown(new int[]{rx, top + 44, rw}, "Brightness", 0, 100, Math.round(hsv[2] * 100), Math.round(hsv[2] * 100) + "%", v -> {
			hsv[2] = v / 100f;
			set.accept(java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF);
		});
		text("Drag the wheel", rx, top + 74, Theme.MUTE);
	}

	/** Splits text into lines no wider than w pixels. */
	public java.util.List<String> wrap(String s, int w) {
		java.util.List<String> out = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			String next = line.length() == 0 ? word : line + " " + word;
			if (ClientFont.width(tr, next) > w && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (line.length() > 0) out.add(line.toString());
		return out;
	}

	/** Wrapped hint text; returns the pixel height used. */
	public int note(String s, int x, int y, int w) {
		int yy = y;
		for (String line : wrap(s, w)) {
			text(line, x, yy, Theme.MUTE);
			yy += 11;
		}
		return yy - y;
	}

	/** Row layout helper: two columns of cards, full-width rows, section headings, wrapped notes. */
	public static final class Form {
		private final Ui ui;
		private final int x, w;
		private int y, col;

		public Form(Ui ui, int x, int y, int w) {
			this.ui = ui;
			this.x = x;
			this.y = y;
			this.w = w;
		}

		private void flush() {
			if (col != 0) {
				col = 0;
				y += ROW;
			}
		}

		public int[] half() {
			int colW = (w - 6) / 2;
			int[] r = {x + col * (colW + 6), y, colW};
			if (++col == 2) {
				col = 0;
				y += ROW;
			}
			return r;
		}

		public int[] full() {
			flush();
			int[] r = {x, y, w};
			y += ROW;
			return r;
		}

		public void section(String title) {
			flush();
			y += 5;
			ui.headText(title, x + 2, y, Theme.TEXT);
			int lx = x + 2 + ui.headWidth(title) + 10;
			ui.rect(lx, y + 5, Math.max(0, x + w - lx), 1, Ui.white(0.10f));
			y += 17;
		}

		public void note(String s) {
			flush();
			y += ui.note(s, x, y, w) + 5;
		}

		public int x() {
			return x;
		}

		public int width() {
			return w;
		}

		/** Reserves a custom-height row and returns its top y. */
		public int reserve(int h) {
			flush();
			int top = y;
			y += h;
			return top;
		}

		public int bottom() {
			flush();
			return y;
		}
	}
}
