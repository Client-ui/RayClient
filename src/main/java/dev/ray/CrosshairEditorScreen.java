package dev.ray;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Draw your own crosshair (normal + enemy) on an 11x11 grid.
 * Left-click / drag draws pixels, right-click / drag erases them.
 */
public class CrosshairEditorScreen extends Screen {
	private static final String[] TARGETS = {"Players", "Living", "Any"};
	private int cell = 14; // grid cell size, smaller on narrow windows

	private final Screen parent;
	private final Ui ui = new Ui();
	private int editTab = 0; // 0 = normal, 1 = enemy
	private int paintMode = 0; // 0 = idle, 1 = drawing, 2 = erasing
	private int gx, gy;

	public CrosshairEditorScreen(Screen parent) {
		super(Text.literal("Crosshair Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ui.reset();
		paintMode = 0;
	}

	private String art() {
		RayConfig c = RayConfig.get();
		return editTab == 0 ? c.crosshairPixels : c.enemyPixels;
	}

	private void setArt(String a) {
		RayConfig c = RayConfig.get();
		if (editTab == 0) c.crosshairPixels = a;
		else c.enemyPixels = a;
	}

	private int colorIdx() {
		RayConfig c = RayConfig.get();
		return editTab == 0 ? c.crosshairColor : c.enemyColor;
	}

	private void setColor(int i) {
		RayConfig c = RayConfig.get();
		if (editTab == 0) c.crosshairColor = i;
		else c.enemyColor = i;
	}

	private int cellAt(int x, int y) {
		if (x < gx || y < gy) return -1;
		int c = (x - gx) / cell, r = (y - gy) / cell;
		if (c < 0 || c >= CrosshairArt.N || r < 0 || r >= CrosshairArt.N) return -1;
		return r * CrosshairArt.N + c;
	}

	/** Compact on/off row. */
	private void smallToggle(int x, int y, int w, String label, boolean on, Runnable flip) {
		boolean hov = ui.over(x, y, w, 20);
		float hv = ui.anim("sth" + label, hov);
		float k = ui.anim("stk" + label, on);
		ui.rrect(x, y, w, 20, 6, Theme.mix(Theme.CARD, Theme.CARD_HOVER, hv));
		ui.ring(x, y, w, 20, 6, Theme.mix(Theme.STROKE, Theme.accent(), hv * 0.45f));
		ui.text(ui.fit(label, w - 44), x + 8, y + 6, Theme.TEXT);
		int tx = x + w - 32, ty = y + 4;
		ui.rrect(tx, ty, 24, 12, 6, Theme.mix(0xFF2A303B, Theme.accent(), k));
		ui.rrect(tx + 2 + Math.round(k * 12f), ty + 2, 8, 8, 4, Theme.mix(0xFFB4BBC9, 0xFFFFFFFF, k));
		ui.click("tg" + label, x, y, w, 20, flip);
	}

	private void smallButton(int x, int y, int w, String label, Runnable action) {
		boolean hov = ui.over(x, y, w, 20);
		float hv = ui.anim("sb" + label, hov);
		ui.rrect(x, y, w, 20, 6, Theme.mix(Theme.CARD, Theme.CARD_HOVER, hv));
		ui.ring(x, y, w, 20, 6, Theme.mix(Theme.STROKE, Theme.accent(), hv * 0.6f));
		int tw = ui.w(label);
		if (tw <= w - 4) {
			ui.center(label, x + w / 2, y + 6, Theme.TEXT);
		} else {
			float sc = (w - 4f) / tw;
			ui.scaledText(label, x + Math.round((w - tw * sc) / 2f), y + Math.round((20 - 8 * sc) / 2f), sc, Theme.TEXT, false);
		}
		ui.click("sb" + label, x, y, w, 20, action);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		if (client.world == null) Brand.backdrop(ctx, width, height, System.currentTimeMillis(), 1.0f);
		RayConfig cfg = RayConfig.get();
		int accent = Theme.accent();
		float sc = UiScale.fit(width, height, 420, 270, cfg.menuScale);
		int viewW = UiScale.view(width, sc), viewH = UiScale.view(height, sc);
		int mx = UiScale.mouse(mouseX, sc), my = UiScale.mouse(mouseY, sc);
		if (client.world != null) ctx.fill(0, 0, width, height, 0x66000000);
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(sc, sc);
		ui.begin(ctx, textRenderer, mx, my);

		int pw = Math.min(420, viewW - 8), ph = 270;
		cell = 15;
		int px = (viewW - pw) / 2, py = Math.max(0, (viewH - ph) / 2);
		ui.shadow(px, py, pw, ph, 12);
		ui.panelGrad(px, py, pw, ph, 12, ui.glass(0xFF13161B), ui.glass(Theme.PANEL), Theme.STROKE);
		ui.rect(px + 16, py + 1, pw - 32, 1, Theme.fade(accent, 0.8f));

		// header
		boolean backHov = ui.over(px + 12, py + 11, 54, 22);
		float bv = ui.anim("cbback", backHov);
		ui.rrect(px + 12, py + 11, 54, 22, 7, Theme.mix(0xFF15181E, 0xFF1F232B, bv));
		ui.ring(px + 12, py + 11, 54, 22, 7, Theme.mix(Theme.STROKE, accent, bv * 0.7f));
		ui.backIcon(px + 20 - Math.round(bv * 2f), py + 18, Theme.mix(Theme.DIM, 0xFFFFFFFF, bv));
		ui.spaced("BACK", px + 31, py + 18, Theme.mix(Theme.DIM, 0xFFFFFFFF, bv), 1);
		ui.click("back", px + 12, py + 11, 54, 22, this::close);
		ui.textShadow("Crosshair Editor", px + 78, py + 18, 0xFFFFFFFF);
		ui.rect(px + 12, py + 40, pw - 24, 1, Theme.fade(Theme.STROKE, 0.9f));

		// tabs
		int tx = px + 16;
		tx += ui.chip(tx, py + 48, "NORMAL", editTab == 0, () -> editTab = 0) + 4;
		ui.chip(tx, py + 48, "ENEMY", editTab == 1, () -> editTab = 1);

		// ---- grid ----
		gx = px + 18;
		gy = py + 72;
		String art = art();
		int col = CrosshairArt.color(colorIdx());
		int gridPx = CrosshairArt.N * cell;
		ui.rrect(gx - 4, gy - 4, gridPx + 8, gridPx + 8, 7, 0xFF090B10);
		ui.ring(gx - 4, gy - 4, gridPx + 8, gridPx + 8, 7, Theme.STROKE);
		int hoverCell = cellAt(mx, my);
		for (int r = 0; r < CrosshairArt.N; r++) {
			for (int c = 0; c < CrosshairArt.N; c++) {
				int x = gx + c * cell, y = gy + r * cell;
				boolean lit = CrosshairArt.get(art, r, c);
				int base = ((r + c) & 1) == 0 ? 0xFF161A22 : 0xFF12161D;
				ctx.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, lit ? col : base);
				if (hoverCell == r * CrosshairArt.N + c) ui.border(x, y, cell, cell, 0xBBFFFFFF);
			}
		}
		ui.border(gx + 5 * cell, gy + 5 * cell, cell, cell, 0x44FFFFFF); // centre marker
		ui.text("Left-click: draw", gx, gy + gridPx + 9, Theme.DIM);
		ui.text("Right-click: erase", gx, gy + gridPx + 21, Theme.DIM);

		// ---- right column ----
		int rx = gx + CrosshairArt.N * cell + 24, rw = px + pw - 18 - rx;
		ui.spaced("COLOR", rx, py + 50, Theme.MUTE, 2);
		int step = Math.min(22, rw / CrosshairArt.colorCount()), swSize = step - 4;
		for (int i = 0; i < CrosshairArt.colorCount(); i++) {
			final int idx = i;
			int sx = rx + i * step, sy = py + 64;
			boolean selc = colorIdx() == i;
			float sv = ui.anim("cbsw" + i, selc);
			ui.rrect(sx, sy, swSize, 16, 5, CrosshairArt.color(i));
			if (sv > 0.02f) ui.ring(sx - 2, sy - 2, swSize + 4, 20, 7, Theme.fade(0xFFFFFFFF, sv));
			ui.click("col" + i, sx, sy, swSize, 16, () -> setColor(idx));
		}

		// preview (dark | light background) - the blend is real here, so you can see what it does
		int pvy = py + 90, pvh = 56;
		ctx.fill(rx, pvy, rx + rw / 2, pvy + pvh, 0xFF1B2130);
		ctx.fill(rx + rw / 2, pvy, rx + rw, pvy + pvh, 0xFFC4CCDB);
		ui.ring(rx - 1, pvy - 1, rw + 2, pvh + 2, 3, Theme.STROKE_HI);
		CrosshairArt.draw(ctx, art, rx + rw / 2, pvy + pvh / 2, cfg.crosshairScale, col, cfg.crosshairOutline, cfg.crosshairVanillaBlend);

		// presets (one row)
		int bw = (rw - 16) / 5;
		int by = pvy + pvh + 8;
		String[] names = {"Clear", "Cross", "Dot", "Ring", "X"};
		for (int i = 0; i < names.length; i++) {
			final int idx = i;
			smallButton(rx + i * (bw + 4), by, bw, names[i], () -> setArt(switch (idx) {
				case 0 -> CrosshairArt.blank();
				case 1 -> CrosshairArt.cross();
				case 2 -> CrosshairArt.dot();
				case 3 -> CrosshairArt.ring();
				default -> CrosshairArt.x();
			}));
		}

		// toggles
		int ty = by + 28;
		smallToggle(rx, ty, rw, "Vanilla Blending", cfg.crosshairVanillaBlend, () -> cfg.crosshairVanillaBlend = !cfg.crosshairVanillaBlend);
		smallToggle(rx, ty + 24, rw, "Outline", cfg.crosshairOutline, () -> cfg.crosshairOutline = !cfg.crosshairOutline);
		if (editTab == 0) {
			smallToggle(rx, ty + 48, rw, "Use Drawn", cfg.crosshairDrawn, () -> cfg.crosshairDrawn = !cfg.crosshairDrawn);
		} else {
			smallToggle(rx, ty + 48, rw, "Enemy Crosshair", cfg.enemyCrosshair, () -> cfg.enemyCrosshair = !cfg.enemyCrosshair);
			smallButton(rx, ty + 72, rw, "Target: " + TARGETS[cfg.enemyTarget], () -> cfg.enemyTarget = (cfg.enemyTarget + 1) % TARGETS.length);
		}

		ui.poll();

		// ---- painting (after the widgets had their chance to take the click) ----
		if (paintMode == 0 && (ui.leftPressed || ui.rightPressed) && cellAt(mx, my) >= 0) {
			paintMode = ui.leftPressed ? 1 : 2;
		}
		if (paintMode != 0) {
			boolean held = paintMode == 1 ? ui.left : ui.right;
			if (!held) {
				paintMode = 0;
				RayConfig.save();
			} else {
				int cellIdx = cellAt(mx, my);
				if (cellIdx >= 0) setArt(CrosshairArt.set(art(), cellIdx / CrosshairArt.N, cellIdx % CrosshairArt.N, paintMode == 1));
			}
		}
		ctx.getMatrices().popMatrix();
	}

	@Override
	public void close() {
		RayConfig.save();
		client.setScreen(parent);
	}
}
