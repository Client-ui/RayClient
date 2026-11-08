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
			int rad = Math.min(90, Math.max(4, Math.round(e * Math.max(r.w(), r.h()) * 0.9f)));
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

	// ---- anti-aliased rounded shapes: the corner pixels get partial alpha, so edges look smooth instead of stair-stepped ----

	private static final java.util.Map<Integer, float[][]> COV = new java.util.HashMap<>();
	private static final java.util.Map<Integer, float[][]> RING = new java.util.HashMap<>();

	private static float clamp01(float v) {
		return v < 0f ? 0f : Math.min(1f, v);
	}

	/** Coverage (0..1) of each pixel of one r x r corner box (row i from the top, column j from the left edge). */
	private static float[][] cov(int r) {
		return COV.computeIfAbsent(r, k -> {
			float[][] t = new float[k][k];
			for (int i = 0; i < k; i++) {
				for (int j = 0; j < k; j++) {
					double d = Math.hypot(k - (j + 0.5), k - (i + 0.5));
					t[i][j] = clamp01((float) (k - d + 0.5));
				}
			}
			return t;
		});
	}

	/** Coverage of the 1px band that follows the outside of the corner (used for outlines). */
	private static float[][] ringCov(int r) {
		return RING.computeIfAbsent(r, k -> {
			float[][] t = new float[k][k];
			for (int i = 0; i < k; i++) {
				for (int j = 0; j < k; j++) {
					double d = Math.hypot(k - (j + 0.5), k - (i + 0.5));
					t[i][j] = Math.max(0f, clamp01((float) (k - d + 0.5)) - clamp01((float) (k - 1 - d + 0.5)));
				}
			}
			return t;
		});
	}

	private static int scaleAlpha(int argb, float f) {
		int a = (int) (((argb >>> 24) & 0xFF) * f);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** Filled rounded rectangle with smooth (anti-aliased) corners. */
	public void rrect(int x, int y, int w, int h, int r, int c) {
		rrectGrad(x, y, w, h, r, c, c);
	}

	/** Per-radius corner shape: for every row, the first fully covered column and the coverage of the two pixels before it. */
	private static final class Corner {
		final int[] inset;
		final float[] e1, e2;

		Corner(int r) {
			inset = new int[r];
			e1 = new float[r];
			e2 = new float[r];
			float[][] t = cov(r);
			for (int i = 0; i < r; i++) {
				int jf = r;
				while (jf > 0 && t[i][jf - 1] >= 0.98f) jf--;
				inset[i] = jf;
				e1[i] = jf >= 1 ? t[i][jf - 1] : 0f;
				e2[i] = jf >= 2 ? t[i][jf - 2] : 0f;
			}
		}
	}

	private static final java.util.Map<Integer, Corner> CORNERS = new java.util.HashMap<>();

	private static Corner corner(int r) {
		return CORNERS.computeIfAbsent(r, Corner::new);
	}

	/** Rounded rectangle filled with a vertical gradient from c1 (top) to c2 (bottom). Few fills per row, so it stays cheap. */
	public void rrectGrad(int x, int y, int w, int h, int r, int c1, int c2) {
		if (w <= 0 || h <= 0) return;
		r = Math.min(r, Math.min(w, h) / 2);
		boolean flat = c1 == c2;
		if (r <= 0) {
			if (flat) ctx.fill(x, y, x + w, y + h, c1);
			else ctx.fillGradient(x, y, x + w, y + h, c1, c2);
			return;
		}
		if (flat) ctx.fill(x, y + r, x + w, y + h - r, c1);
		else ctx.fillGradient(x, y + r, x + w, y + h - r, Theme.mix(c1, c2, r / (float) h), Theme.mix(c1, c2, (h - r) / (float) h));
		Corner cn = corner(r);
		for (int i = 0; i < r; i++) {
			int top = flat ? c1 : Theme.mix(c1, c2, (i + 0.5f) / h);
			int bot = flat ? c1 : Theme.mix(c1, c2, (h - i - 0.5f) / h);
			int jf = cn.inset[i];
			ctx.fill(x + jf, y + i, x + w - jf, y + i + 1, top);
			ctx.fill(x + jf, y + h - 1 - i, x + w - jf, y + h - i, bot);
			float c1v = cn.e1[i];
			if (c1v >= 0.04f && jf >= 1) {
				int j = jf - 1;
				int ct = scaleAlpha(top, c1v), cb = scaleAlpha(bot, c1v);
				ctx.fill(x + j, y + i, x + j + 1, y + i + 1, ct);
				ctx.fill(x + w - 1 - j, y + i, x + w - j, y + i + 1, ct);
				ctx.fill(x + j, y + h - 1 - i, x + j + 1, y + h - i, cb);
				ctx.fill(x + w - 1 - j, y + h - 1 - i, x + w - j, y + h - i, cb);
			}
			float c2v = cn.e2[i];
			if (c2v >= 0.04f && jf >= 2) {
				int j = jf - 2;
				int ct = scaleAlpha(top, c2v), cb = scaleAlpha(bot, c2v);
				ctx.fill(x + j, y + i, x + j + 1, y + i + 1, ct);
				ctx.fill(x + w - 1 - j, y + i, x + w - j, y + i + 1, ct);
				ctx.fill(x + j, y + h - 1 - i, x + j + 1, y + h - i, cb);
				ctx.fill(x + w - 1 - j, y + h - 1 - i, x + w - j, y + h - i, cb);
			}
		}
	}

	/** A 1px smooth outline that follows the same rounded shape as {@link #rrect}. Works with translucent colours. */
	public void ring(int x, int y, int w, int h, int r, int c) {
		if (w <= 1 || h <= 1) return;
		r = Math.min(r, Math.min(w, h) / 2);
		if (r <= 0) {
			border(x, y, w, h, c);
			return;
		}
		ctx.fill(x + r, y, x + w - r, y + 1, c);
		ctx.fill(x + r, y + h - 1, x + w - r, y + h, c);
		ctx.fill(x, y + r, x + 1, y + h - r, c);
		ctx.fill(x + w - 1, y + r, x + w, y + h - r, c);
		float[][] t = ringCov(r);
		for (int i = 0; i < r; i++) {
			for (int j = 0; j < r; j++) {
				float cv = t[i][j];
				if (cv < 0.04f) continue;
				int cc = scaleAlpha(c, cv);
				ctx.fill(x + j, y + i, x + j + 1, y + i + 1, cc);
				ctx.fill(x + w - 1 - j, y + i, x + w - j, y + i + 1, cc);
				ctx.fill(x + j, y + h - 1 - i, x + j + 1, y + h - i, cc);
				ctx.fill(x + w - 1 - j, y + h - 1 - i, x + w - j, y + h - i, cc);
			}
		}
	}

	/** Filled circle (smooth edge). */
	public void circle(int cx, int cy, int r, int c) {
		rrect(cx - r, cy - r, r * 2, r * 2, r, c);
	}

	/** Rounded panel with a 1px outline. The fill may be translucent (glass). */
	public void panel(int x, int y, int w, int h, int r, int fill, int stroke) {
		rrect(x, y, w, h, r, fill);
		ring(x, y, w, h, r, stroke);
	}

	/** Rounded panel with a vertical gradient fill and a 1px outline. */
	public void panelGrad(int x, int y, int w, int h, int r, int top, int bottom, int stroke) {
		rrectGrad(x, y, w, h, r, top, bottom);
		ring(x, y, w, h, r, stroke);
	}

	/** Soft drop shadow made of stacked translucent layers that fall off smoothly (reads as a real blur). */
	public void shadow(int x, int y, int w, int h, int r) {
		if (!RayConfig.get().uiShadows) return;
		for (int k = 12; k >= 2; k -= 2) {
			float f = 1f - k / 13f;
			rrect(x - k, y - k + 4, w + 2 * k, h + 2 * k, r + k, Theme.fade(0xFF000000, 0.05f * f * f + 0.004f));
		}
	}

	/** A coloured halo around a shape (hovered or enabled cards, focused fields). strength is 0..1. */
	public void glow(int x, int y, int w, int h, int r, int color, float strength) {
		if (strength < 0.02f || !RayConfig.get().uiShadows) return;
		for (int k = 6; k >= 2; k -= 2) {
			float f = 1f - k / 7f;
			rrect(x - k, y - k, w + 2 * k, h + 2 * k, r + k, Theme.fade(color, 0.11f * f * f * strength));
		}
	}

	/** The window colour made see-through according to the Glass setting, so the blurred game shows behind it. */
	public int glass(int opaque) {
		return Theme.fade(opaque, RayConfig.get().uiGlass / 100.0f);
	}

	// ---------- surfaces (the new look) ----------

	/**
	 * A floating glass surface: soft shadow, a vertical glass gradient, a hairline border, a bright top edge and a
	 * thin accent light along the top that fades out at both ends.
	 */
	public void surface(int x, int y, int w, int h, int r) {
		shadow(x, y, w, h, r);
		rrectGrad(x, y, w, h, r, glass(0xFF191C23), glass(0xFF0F1115));
		ring(x, y, w, h, r, Theme.STROKE);
		accentLine(x + r, y, w - 2 * r, Theme.accent());
	}

	/** A 1px line that is brightest in the middle and fades toward both ends. */
	public void accentLine(int x, int y, int w, int color) {
		if (w <= 8) return;
		final int segs = 20;
		for (int i = 0; i < segs; i++) {
			int x1 = x + w * i / segs, x2 = x + w * (i + 1) / segs;
			double a = Math.sin(((i + 0.5) / segs) * Math.PI);
			ctx.fill(x1, y, x2, y + 1, Theme.fade(color, (float) (0.80 * a * a)));
		}
	}

	/** A rounded plate behind an icon, tinted by tint (strength 0..1 = how strongly). */
	public void plate(int x, int y, int size, int tint, float strength) {
		int r = Math.max(4, size / 4);
		rrectGrad(x, y, size, size, r, Theme.mix(0xFF20242C, tint, 0.10f + 0.26f * strength), Theme.mix(0xFF15181E, tint, 0.05f + 0.14f * strength));
		ring(x, y, size, size, r, Theme.mix(Theme.STROKE, tint, 0.25f + 0.55f * strength));
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
	private static final String[] GEAR = {"....#....", ".#..#..#.", "..#####..", "..##.##..", "###...###", "..##.##..", "..#####..", ".#..#..#.", "....#...."};
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

	/** Cog glyph, 9x9. */
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

	/** Width of text drawn with {@link #spaced} (sp extra pixels between letters). */
	public int spacedWidth(String s, int sp) {
		int w = 0;
		for (int i = 0; i < s.length(); i++) w += ClientFont.width(tr, ch(s.charAt(i))) + sp;
		return Math.max(0, w - sp);
	}

	/** Letter-spaced text, used for tabs and headings. */
	public void spaced(String s, int x, int y, int c, int sp) {
		int cx = x;
		for (int i = 0; i < s.length(); i++) {
			String g = ch(s.charAt(i));
			if (s.charAt(i) != ' ') ClientFont.draw(ctx, tr, g, cx, y, c, false);
			cx += ClientFont.width(tr, g) + sp;
		}
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

	/** Filter chip. Returns its width so callers can lay chips out in a row. */
	public int chip(int x, int y, String label, boolean active, Runnable click) {
		int w = ClientFont.width(tr, label) + 14;
		float a = anim("chip" + label, over(x, y, w, 14));
		float on = anim("chipon" + label, active);
		int accent = Theme.accent();
		rrect(x, y, w, 14, 5, Theme.mix(Theme.mix(Theme.CARD, Theme.CARD_HOVER, a), Theme.mix(Theme.CARD, accent, 0.30f), on));
		ring(x, y, w, 14, 5, Theme.mix(Theme.STROKE, accent, on * 0.85f));
		text(label, x + 7, y + 3, active ? 0xFFFFFFFF : Theme.mix(Theme.DIM, 0xFFFFFFFF, a * 0.6f));
		click("chip" + label, x, y, w, 14, click);
		return w;
	}

	/** Square icon button; draw the glyph yourself via the returned hover flag. */
	public boolean iconButton(int x, int y, int w, int h, int base, int hover, Runnable click) {
		boolean hov = over(x, y, w, h);
		float a = anim("ib" + x + "," + y, hov);
		float p = anim("ibp" + x + "," + y, held(x, y, w, h));
		rrect(x, y + Math.round(p), w, h, 6, Theme.mix(base, hover, a));
		click("ib" + x + "," + y, x, y, w, h, click);
		return hov;
	}

	public void labelButton(int x, int y, int w, int h, String label, int color, Runnable action) {
		boolean hov = over(x, y, w, h);
		float a = anim("lb" + label + x, hov);
		float p = anim("lbp" + label + x, held(x, y, w, h));
		int yy = y + Math.round(p);
		rrectGrad(x, yy, w, h, 6, Theme.fade(Theme.mix(color, 0xFFFFFFFF, 0.10f), 0.80f + 0.20f * a), Theme.fade(color, 0.80f + 0.20f * a));
		center(label, x + w / 2, yy + (h - 8) / 2, 0xFFFFFFFF);
		click("lb" + label + x + "," + y, x, y, w, h, action);
	}

	/** A generic button: dark surface with a tint colour on hover. enabled=false greys it out. */
	public void pill(int x, int y, int w, int h, String label, int tint, boolean enabled, Runnable action) {
		boolean hov = enabled && over(x, y, w, h);
		float a = anim("pl" + label + x + "," + y, hov);
		float p = anim("plp" + label + x + "," + y, enabled && held(x, y, w, h));
		int yy = y + Math.round(p);
		int fillTop = enabled ? Theme.mix(0xFF1B1F26, Theme.mix(0xFF1B1F26, tint, 0.38f), a) : 0xFF12141A;
		int fillBot = enabled ? Theme.mix(0xFF14171D, Theme.mix(0xFF14171D, tint, 0.26f), a) : 0xFF101217;
		rrectGrad(x, yy, w, h, 6, fillTop, fillBot);
		ring(x, yy, w, h, 6, enabled ? Theme.mix(Theme.STROKE, tint, 0.35f + 0.55f * a) : 0xFF1E222A);
		spacedCenter(label, x + w / 2, yy + (h - 8) / 2, enabled ? Theme.mix(0xFFD5DAE6, 0xFFFFFFFF, a) : Theme.MUTE, 1);
		if (enabled) click("pl" + label + x + "," + y, x, y, w, h, action);
	}

	public void scrollbar(int x, int y, int h, int viewH, int contentH, int scroll) {
		if (contentH <= viewH) return;
		boolean hov = over(x - 3, y, 9, h);
		float a = anim("sbar" + x + "," + y, hov);
		int w = 3 + Math.round(a);
		rrect(x, y, w, h, w / 2, 0x16FFFFFF);
		int th = Math.max(18, h * viewH / contentH);
		int ty = y + (int) ((long) (h - th) * scroll / Math.max(1, contentH - viewH));
		rrect(x, ty, w, th, w / 2, Theme.fade(Theme.accent(), 0.65f + 0.3f * a));
	}

	/** A progress bar with a soft moving shine on the filled part. frac is 0..1. */
	public void progress(int x, int y, int w, int h, float frac, int color) {
		rrect(x, y, w, h, h / 2, 0xFF1E222A);
		ring(x, y, w, h, h / 2, 0xFF2A2F3A);
		int fw = Math.round((w - 2) * Theme.clamp01(frac));
		if (fw < h) fw = frac <= 0.001f ? 0 : h;
		if (fw <= 0) return;
		rrectGrad(x + 1, y + 1, fw, h - 2, Math.max(1, (h - 2) / 2), Theme.mix(color, 0xFFFFFFFF, 0.18f), color);
		if (RayConfig.get().uiAnimations && fw > 12) {
			float t = (System.currentTimeMillis() % 1400L) / 1400f;
			int sx = x + 1 + Math.round((fw + 24) * t) - 24;
			ctx.enableScissor(x + 1, y + 1, x + 1 + fw, y + h - 1);
			ctx.fillGradient(sx, y + 1, sx + 12, y + h - 1, 0x00FFFFFF, 0x30FFFFFF);
			ctx.fillGradient(sx + 12, y + 1, sx + 24, y + h - 1, 0x30FFFFFF, 0x00FFFFFF);
			ctx.disableScissor();
		}
	}

	// ---------- settings rows (r = {x, y, width}) ----------

	private void card(int[] r, String id) {
		float a = anim("h" + id, over(r[0], r[1], r[2], H));
		rrectGrad(r[0], r[1], r[2], H, 7, Theme.mix(0xFF1A1D24, 0xFF22262E, a), Theme.mix(0xFF14171D, 0xFF1B1F26, a));
		ring(r[0], r[1], r[2], H, 7, Theme.mix(Theme.STROKE, Theme.accent(), a * 0.5f));
	}

	public void toggle(int[] r, String name, boolean on, Runnable flip) {
		card(r, "t" + name);
		float k = anim("k" + name, on);
		if (k > 0.02f) rrectGrad(r[0] + 1, r[1] + 1, r[2] - 2, H - 2, 6, Theme.fade(Theme.accent(), 0.10f * k), Theme.fade(Theme.accent(), 0.02f * k));
		text(fit(name, r[2] - 64), r[0] + 11, r[1] + 8, Theme.mix(Theme.DIM, Theme.TEXT, 0.4f + 0.6f * k));
		int tx = r[0] + r[2] - 40, ty = r[1] + 4;
		if (k > 0.3f) ring(tx - 1, ty - 1, 34, 18, 9, Theme.fade(Theme.accent(), 0.32f * k));
		rrectGrad(tx, ty, 32, 16, 8, Theme.mix(0xFF262B35, Theme.mix(Theme.accent(), 0xFFFFFFFF, 0.12f), k), Theme.mix(0xFF1C2028, Theme.accent(), k));
		circle(tx + 8 + Math.round(k * 16f), ty + 8, 5, Theme.mix(0xFF9AA2B3, 0xFFFFFFFF, k));
		click("t" + name, r[0], r[1], r[2], H, flip);
	}

	public void choice(int[] r, String name, String value, Runnable next) {
		card(r, "c" + name);
		String v = fit(value, r[2] / 2);
		text(fit(name, r[2] - w(v) - 30), r[0] + 10, r[1] + 8, Theme.TEXT);
		right(v, r[0] + r[2] - 17, r[1] + 8, Theme.accent());
		chevron(r[0] + r[2] - 11, r[1] + 9, Theme.MUTE);
		click("c" + name, r[0], r[1], r[2], H, next);
	}

	public void colorChoice(int[] r, String name, String valueName, int swatch, Runnable next) {
		card(r, "cc" + name);
		text(fit(name, r[2] - 80), r[0] + 10, r[1] + 8, Theme.TEXT);
		rrect(r[0] + r[2] - 24, r[1] + 5, 14, 14, 4, swatch);
		ring(r[0] + r[2] - 24, r[1] + 5, 14, 14, 4, 0x55FFFFFF);
		right(valueName, r[0] + r[2] - 30, r[1] + 8, Theme.DIM);
		click("cc" + name, r[0], r[1], r[2], H, next);
	}

	/** Slider with a fully custom value label. */
	public void sliderShown(int[] r, String name, int min, int max, int value, String shown, IntConsumer set) {
		card(r, "s" + name);
		text(fit(name, r[2] - w(shown) - 24), r[0] + 10, r[1] + 4, Theme.TEXT);
		right(shown, r[0] + r[2] - 10, r[1] + 4, Theme.DIM);
		final int bx = r[0] + 10, bw = r[2] - 20, by = r[1] + 16;
		String id = "s" + name + "@" + r[0] + "," + r[1];
		boolean active = dragId != null && dragId.equals(id);
		float hv = anim("sk" + id, active || over(r[0], r[1] + 10, r[2], 14));
		rrect(bx, by, bw, 4, 2, 0xFF2A303B);
		float target = (value - min) / (float) Math.max(1, max - min);
		float shownFill = animTo("sf" + id, Math.max(0f, Math.min(1f, target)));
		int fill = Math.round(shownFill * bw);
		rrectGrad(bx, by, Math.max(4, fill), 4, 2, Theme.mix(Theme.accent(), 0xFFFFFFFF, 0.18f), Theme.accent());
		int kr = 5;
		if (hv > 0.02f) circle(bx + fill, by + 2, kr + 3, Theme.fade(Theme.accent(), 0.28f * hv));
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

	public void button(int[] r, String label, Runnable action) {
		boolean hov = over(r[0], r[1], r[2], H);
		float a = anim("b" + label, hov);
		float p = anim("bp" + label, held(r[0], r[1], r[2], H));
		int yy = r[1] + Math.round(p);
		int accent = Theme.accent();
		int top = Theme.mix(Theme.mix(Theme.CARD_HOVER, accent, 0.30f), Theme.mix(Theme.CARD_HOVER, accent, 0.58f), a);
		int bot = Theme.mix(Theme.mix(Theme.CARD, accent, 0.20f), Theme.mix(Theme.CARD, accent, 0.44f), a);
		rrectGrad(r[0], yy, r[2], H, 6, top, bot);
		ring(r[0], yy, r[2], H, 6, Theme.fade(accent, 0.45f + 0.4f * a));
		center(label, r[0] + r[2] / 2, yy + 8, 0xFFFFFFFF);
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
		panel(x, top, w, boxH, 6, Theme.CARD, Theme.STROKE);

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
			y += 4;
			String t = title.toUpperCase();
			ui.circle(x + 3, y + 4, 2, Theme.accent());
			ui.spaced(t, x + 10, y, Theme.DIM, 1);
			int lx = x + 10 + ui.spacedWidth(t, 1) + 8;
			ui.rect(lx, y + 4, Math.max(0, x + w - lx), 1, Theme.fade(Theme.STROKE, 0.85f));
			y += 16;
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
