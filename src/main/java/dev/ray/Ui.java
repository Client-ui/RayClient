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
 */
public final class Ui {
	public static final int H = 24;
	public static final int ROW = 28;

	private record Hit(String id, int x, int y, int w, int h, Runnable click, Runnable rclick, IntConsumer drag) {
		boolean contains(int px, int py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

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
	private boolean clipped = false;
	private int clipX1, clipY1, clipX2, clipY2;

	// ---------- frame lifecycle ----------

	/** Call from Screen#init so the click that opened the screen is not re-used. */
	public void reset() {
		leftWas = true;
		rightWas = true;
		dragId = null;
		left = right = leftPressed = rightPressed = false;
	}

	public void begin(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
		this.ctx = ctx;
		this.tr = tr;
		this.mx = mouseX;
		this.my = mouseY;
		long now = System.nanoTime();
		dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		hits.clear();
		clipped = false;
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

		if (leftPressed) {
			for (Hit h : hits) {
				if (!h.contains(mx, my)) continue;
				if (h.click() == null && h.drag() == null) continue;
				if (h.click() != null) {
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
		if (clipped && (mx < clipX1 || mx >= clipX2 || my < clipY1 || my >= clipY2)) return false;
		return mx >= x && mx < x + w && my >= y && my < y + h;
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
		for (int k = 8; k >= 2; k -= 3) {
			float f = 1f - k / 9f;
			rrect(x - k, y - k + 3, w + 2 * k, h + 2 * k, r + k, Theme.fade(0xFF000000, 0.07f * f * f + 0.006f));
		}
	}

	/** A coloured halo around a shape (hovered or enabled cards, focused fields). strength is 0..1. */
	public void glow(int x, int y, int w, int h, int r, int color, float strength) {
		if (strength < 0.02f || !RayConfig.get().uiShadows) return;
		// a restrained halo: two faint layers, enough to mark focus without looking neon
		for (int k = 4; k >= 2; k -= 2) {
			float f = 1f - k / 5f;
			rrect(x - k, y - k, w + 2 * k, h + 2 * k, r + k, Theme.fade(color, 0.10f * f * f * strength));
		}
	}

	/** The window colour made see-through according to the Glass setting, so the blurred game shows behind it. */
	public int glass(int opaque) {
		return Theme.fade(opaque, RayConfig.get().uiGlass / 100.0f);
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
		float v = cur + (target - cur) * (1.0f - (float) Math.exp(-dt * 15.0f));
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
		return Theme.easeOutCubic((now - start) / 300.0f);
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

	/** Draws a 1-bit pixel picture ('#' = lit) with the given pixel size. */
	public void pix(String[] rows, int x, int y, int size, int c) {
		for (int r = 0; r < rows.length; r++) {
			String row = rows[r];
			for (int col = 0; col < row.length(); col++) {
				if (row.charAt(col) == '#') ctx.fill(x + col * size, y + r * size, x + (col + 1) * size, y + (r + 1) * size, c);
			}
		}
	}

	private static final String[] HEART = {".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."};
	private static final String[] CLOSE = {"#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"};
	private static final String[] BACK = {"...#", "..##", ".###", "####", ".###", "..##", "...#"};

	public void heart(int x, int y, int c) {
		pix(HEART, x, y, 1, c);
	}

	public void closeIcon(int x, int y, int c) {
		pix(CLOSE, x, y, 1, c);
	}

	public void backIcon(int x, int y, int c) {
		pix(BACK, x, y, 1, c);
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

	// ---------- small widgets ----------

	/** Filter chip. Returns its width so callers can lay chips out in a row. */
	public int chip(int x, int y, String label, boolean active, Runnable click) {
		int w = ClientFont.width(tr, label) + 12;
		float a = anim("chip" + label, over(x, y, w, 14));
		float on = anim("chipon" + label, active);
		int accent = Theme.accent();
		rrect(x, y, w, 14, 4, Theme.mix(Theme.mix(Theme.CARD, Theme.CARD_HOVER, a), Theme.mix(Theme.CARD, accent, 0.30f), on));
		ring(x, y, w, 14, 4, Theme.mix(Theme.STROKE, accent, on * 0.85f));
		text(label, x + 6, y + 3, active ? 0xFFFFFFFF : Theme.mix(Theme.DIM, 0xFFFFFFFF, a * 0.6f));
		click("chip" + label, x, y, w, 14, click);
		return w;
	}

	/** Square icon button; draw the glyph yourself via the returned hover flag. */
	public boolean iconButton(int x, int y, int w, int h, int base, int hover, Runnable click) {
		boolean hov = over(x, y, w, h);
		float a = anim("ib" + x + "," + y, hov);
		rrect(x, y, w, h, 5, Theme.mix(base, hover, a));
		click("ib" + x + "," + y, x, y, w, h, click);
		return hov;
	}

	public void labelButton(int x, int y, int w, int h, String label, int color, Runnable action) {
		float a = anim("lb" + label + x, over(x, y, w, h));
		rrect(x, y, w, h, 5, Theme.fade(color, 0.78f + 0.22f * a));
		center(label, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
		click("lb" + label + x + "," + y, x, y, w, h, action);
	}

	public void scrollbar(int x, int y, int h, int viewH, int contentH, int scroll) {
		if (contentH <= viewH) return;
		rrect(x + 1, y, 2, h, 1, 0x1FFFFFFF);
		int th = Math.max(14, h * viewH / contentH);
		int ty = y + (int) ((long) (h - th) * scroll / Math.max(1, contentH - viewH));
		rrect(x + 1, ty, 2, th, 1, Theme.fade(Theme.accent(), 0.85f));
	}

	// ---------- settings rows (r = {x, y, width}) ----------

	private void card(int[] r, String id) {
		float a = anim("h" + id, over(r[0], r[1], r[2], H));
		rrect(r[0], r[1], r[2], H, 5, Theme.mix(Theme.CARD, Theme.CARD_HOVER, a));
		ring(r[0], r[1], r[2], H, 5, Theme.mix(Theme.STROKE, Theme.accent(), a * 0.35f));
	}

	public void toggle(int[] r, String name, boolean on, Runnable flip) {
		card(r, "t" + name);
		text(fit(name, r[2] - 54), r[0] + 8, r[1] + 8, Theme.TEXT);
		int tx = r[0] + r[2] - 33, ty = r[1] + 6;
		float k = anim("k" + name, on);
		rrect(tx, ty, 25, 12, 6, Theme.mix(0xFF2E3441, Theme.accent(), k));
		rrect(tx + 2 + Math.round(k * 13f), ty + 2, 8, 8, 4, Theme.mix(0xFFB4BBC9, 0xFFFFFFFF, k));
		click("t" + name, r[0], r[1], r[2], H, flip);
	}

	public void choice(int[] r, String name, String value, Runnable next) {
		card(r, "c" + name);
		String v = fit(value, r[2] / 2);
		text(fit(name, r[2] - w(v) - 24), r[0] + 8, r[1] + 8, Theme.TEXT);
		right(v, r[0] + r[2] - 8, r[1] + 8, Theme.accent());
		click("c" + name, r[0], r[1], r[2], H, next);
	}

	public void colorChoice(int[] r, String name, String valueName, int swatch, Runnable next) {
		card(r, "cc" + name);
		text(fit(name, r[2] - 70), r[0] + 8, r[1] + 8, Theme.TEXT);
		rrect(r[0] + r[2] - 22, r[1] + 5, 14, 14, 3, swatch);
		right(valueName, r[0] + r[2] - 28, r[1] + 8, Theme.DIM);
		click("cc" + name, r[0], r[1], r[2], H, next);
	}

	/** Slider with a fully custom value label. */
	public void sliderShown(int[] r, String name, int min, int max, int value, String shown, IntConsumer set) {
		card(r, "s" + name);
		text(fit(name, r[2] - w(shown) - 20), r[0] + 8, r[1] + 4, Theme.TEXT);
		right(shown, r[0] + r[2] - 8, r[1] + 4, Theme.DIM);
		final int bx = r[0] + 8, bw = r[2] - 16, by = r[1] + 16;
		String id = "s" + name + "@" + r[0] + "," + r[1];
		boolean active = dragId != null && dragId.equals(id);
		float hv = anim("sk" + id, active || over(r[0], r[1] + 10, r[2], 14));
		rrect(bx, by + 1, bw, 3, 1, 0xFF2E3441);
		float target = (value - min) / (float) Math.max(1, max - min);
		float shownFill = animTo("sf" + id, Math.max(0f, Math.min(1f, target)));
		int fill = Math.round(shownFill * bw);
		rrect(bx, by + 1, Math.max(3, fill), 3, 1, Theme.accent());
		int kr = 4;
		if (hv > 0.02f) ring(bx + fill - kr - 2, by + 2 - kr - 2, (kr + 2) * 2, (kr + 2) * 2, kr + 2, Theme.fade(Theme.accent(), 0.5f * hv));
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
		float a = anim("b" + label, over(r[0], r[1], r[2], H));
		int accent = Theme.accent();
		rrect(r[0], r[1], r[2], H, 5, Theme.mix(Theme.mix(Theme.CARD_HOVER, accent, 0.28f), Theme.mix(Theme.CARD_HOVER, accent, 0.55f), a));
		ring(r[0], r[1], r[2], H, 5, Theme.fade(accent, 0.45f + 0.4f * a));
		center(label, r[0] + r[2] / 2, r[1] + 8, 0xFFFFFFFF);
		click("b" + label, r[0], r[1], r[2], H, action);
	}

	// ---------- colour wheel ----------

	private final java.util.Map<String, float[]> hsvState = new java.util.HashMap<>();

	/**
	 * A colour wheel: drag around the disc to pick hue (angle) and saturation (distance from the centre), with a
	 * brightness slider, a preview and the hex code. rgb is 0xRRGGBB. Takes one tall block of the form.
	 */
	public void colorPicker(Form f, String id, int rgb, IntConsumer set) {
		final int R = 42, boxH = 94;
		int x = f.x(), w = f.width();
		int top = f.reserve(boxH + 6);
		panel(x, top, w, boxH, 3, Theme.CARD, Theme.STROKE);

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
		for (int py = -R; py < R; py += 2) {
			for (int px = -R; px < R; px += 2) {
				double dx = px + 1, dy = py + 1;
				double r = Math.sqrt(dx * dx + dy * dy);
				if (r > R) continue;
				float hue = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1.0) % 1.0);
				ctx.fill(cx + px, cy + py, cx + px + 2, cy + py + 2, 0xFF000000 | java.awt.Color.HSBtoRGB(hue, (float) (r / R), hsv[2]));
			}
		}
		// marker
		double ang = hsv[0] * 2 * Math.PI;
		int mx2 = cx + (int) Math.round(Math.cos(ang) * hsv[1] * R), my2 = cy + (int) Math.round(Math.sin(ang) * hsv[1] * R);
		border(mx2 - 3, my2 - 3, 7, 7, 0xFF000000);
		border(mx2 - 2, my2 - 2, 5, 5, 0xFFFFFFFF);
		hit("wheel" + id, cx - R, cy - R, R * 2, R * 2, null, null, m -> {
			double dx = m - cx, dy = this.my - cy;
			hsv[0] = (float) ((Math.atan2(dy, dx) / (2 * Math.PI) + 1.0) % 1.0);
			hsv[1] = (float) Math.min(1.0, Math.sqrt(dx * dx + dy * dy) / R);
			set.accept(java.awt.Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF);
		});

		int rx = x + 8 + R * 2 + 14, rw = w - (rx - x) - 8;
		rrect(rx, top + 6, rw, 20, 3, 0xFF000000 | (rgb & 0xFFFFFF));
		border(rx, top + 6, rw, 20, Theme.STROKE);
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
			if (tr.getWidth(next) > w && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (line.length() > 0) out.add(line.toString());
		return out;
	}

	/** Two-line wrapped hint text; returns the pixel height used. */
	public int note(String s, int x, int y, int w) {
		int yy = y;
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			String next = line.length() == 0 ? word : line + " " + word;
			if (ClientFont.width(tr, next) > w && line.length() > 0) {
				text(line.toString(), x, yy, Theme.MUTE);
				yy += 11;
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (line.length() > 0) {
			text(line.toString(), x, yy, Theme.MUTE);
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
			y += 3;
			String t = title.toUpperCase();
			ui.rect(x, y + 1, 2, 7, Theme.accent());
			ui.text(t, x + 6, y, Theme.DIM);
			int lx = x + 6 + ui.w(t) + 6;
			ui.rect(lx, y + 4, Math.max(0, x + w - lx), 1, Theme.STROKE);
			y += 14;
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
