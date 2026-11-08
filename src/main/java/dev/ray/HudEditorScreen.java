package dev.ray;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD editor: drag modules (they snap to screen edges, the centre and each other, with guide lines), right-click one for
 * its settings. The toolbar at the bottom has Mods, Visuals, Profiles, Auto Arrange and Reset Positions. The toolbar is
 * scaled to fit the window; the modules themselves are always shown at their real size and position.
 */
public class HudEditorScreen extends Screen {
	private HudModule dragging = null;
	private int offX, offY;
	private boolean mouseWasDown = true, rightWasDown = true;
	private int guideX = -1, guideY = -1;
	private final long openedAt = System.currentTimeMillis();
	private final Ui ui = new Ui();

	public HudEditorScreen() {
		super(Text.literal("HUD Editor"));
	}

	@Override
	protected void init() {
		mouseWasDown = true;
		rightWasDown = true;
		dragging = null;
		ui.reset();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Lays every visible module out in columns so nothing overlaps. */
	private void autoArrange(List<HudModule> active) {
		int x = 4, y = 4, colW = 0, gap = 3;
		for (HudModule m : active) {
			if (m == Modules.scoreboard) continue;
			int ew = m.ew(), eh = m.eh();
			if (y + eh > height - 4 && y > 4) {
				x += colW + 6;
				y = 4;
				colW = 0;
			}
			m.moveTo(x, y, width, height);
			y += eh + gap;
			colW = Math.max(colW, ew);
		}
		RayConfig.save();
		Toasts.push("HUD arranged");
	}

	private void openMods(String filter, String pageName) {
		RayScreen.selectPage("MODS");
		RayScreen.selectFilter(filter);
		if (pageName != null) RayScreen.selectPage(pageName);
		client.setScreen(new RayScreen(this));
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		RayConfig cfg = RayConfig.get();
		int accent = Theme.accent();
		long now = System.currentTimeMillis();
		float open = cfg.uiAnimations ? Theme.easeOutCubic((now - openedAt) / 240f) : 1f;
		ctx.fill(0, 0, width, height, Theme.fade(0xFF000000, 0.42f * open));
		// faint centre lines help to line things up
		ctx.fill(width / 2, 0, width / 2 + 1, height, Theme.fade(0xFFFFFFFF, 0.05f * open));
		ctx.fill(0, height / 2, width, height / 2 + 1, Theme.fade(0xFFFFFFFF, 0.05f * open));

		List<HudModule> active = new ArrayList<>();
		for (HudModule m : Modules.ALL) {
			if (!m.enabled()) continue;
			if (!m.prepare(client, textRenderer, true)) continue;
			if (m == dragging) {
				m.x = Math.max(0, Math.min(width - m.ew(), m.x));
				m.y = Math.max(0, Math.min(height - m.eh(), m.y));
			} else {
				m.place(width, height);
			}
			active.add(m);
		}

		// toolbar geometry (needed first so clicks on it do not start a drag)
		float ts = Math.min(1.0f, UiScale.fit(width, height, 520, 200, 100));
		int tvw = UiScale.view(width, ts), tvh = UiScale.view(height, ts);
		int tbW = Math.min(400, tvw - 12), tbH = 54;
		int tbX = (tvw - tbW) / 2, tbY = tvh - tbH - 12;
		int rx = Math.round(tbX * ts), ry = Math.round(tbY * ts), rw = Math.round(tbW * ts), rh = Math.round(tbH * ts);
		// centre cluster: logo, name and the Mods / Settings buttons (virtual units of the same scale)
		int clW = 150, clH = 98;
		int clX = (tvw - clW) / 2, clY = (tvh - clH) / 2;
		int crx = Math.round(clX * ts), cry = Math.round(clY * ts), crw = Math.round(clW * ts), crh = Math.round(clH * ts);
		boolean overToolbar = inside(mouseX, mouseY, rx, ry, rw, rh) || inside(mouseX, mouseY, crx, cry, crw, crh);

		ui.begin(ctx, textRenderer, mouseX, mouseY);
		for (HudModule m : active) {
			m.render(ctx, client, textRenderer);
			boolean hot = m == dragging || (dragging == null && !overToolbar && inside(mouseX, mouseY, m.x, m.y, m.ew(), m.eh()));
			float hv = ui.anim("hm" + m.id, hot);
			if (hv > 0.02f) ctx.fill(m.x, m.y, m.x + m.ew(), m.y + m.eh(), Theme.fade(0xFFFFFFFF, 0.08f * hv));
			ui.ring(m.x - 2, m.y - 2, m.ew() + 4, m.eh() + 4, 3, hot ? 0xFFFFFFFF : Theme.fade(accent, 0.7f));
			if (hot) {
				String tag = m == dragging ? m.name + "  " + m.x + ", " + m.y : m.name;
				int tw = ui.w(tag) + 12;
				int tx = Math.max(2, Math.min(width - tw - 2, m.x));
				int ty = m.y - 18 >= 2 ? m.y - 18 : m.y + m.eh() + 4;
				ui.shadow(tx, ty, tw, 14, 5);
				ui.panel(tx, ty, tw, 14, 5, 0xF2111318, Theme.fade(accent, 0.8f));
				ui.text(tag, tx + 6, ty + 3, 0xFFFFFFFF);
			}
		}
		// guide lines while dragging
		if (dragging != null) {
			if (guideX >= 0) ctx.fill(guideX, 0, guideX + 1, height, Theme.fade(accent, 0.65f));
			if (guideY >= 0) ctx.fill(0, guideY, width, guideY + 1, Theme.fade(accent, 0.65f));
		}

		// title
		String title = "HUD EDITOR";
		String hint = "Drag to move   -   Right-click for settings   -   ESC to close";
		int hw = Math.max(ui.spacedWidth(title, 2), ui.w(hint)) + 36;
		int hx = (width - hw) / 2, hy = 8 - Math.round((1f - open) * 20f);
		ui.shadow(hx, hy, hw, 34, 9);
		ui.panelGrad(hx, hy, hw, 34, 9, 0xF2171A20, 0xF2101217, Theme.STROKE_HI);
		ui.spacedCenter(title, width / 2, hy + 7, 0xFFFFFFFF, 2);
		ui.rrect(width / 2 - 12, hy + 18, 24, 2, 1, accent);
		ui.center(hint, width / 2, hy + 22, Theme.DIM);

		// toolbar (scaled)
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(ts, ts);
		ui.begin(ctx, textRenderer, UiScale.mouse(mouseX, ts), UiScale.mouse(mouseY, ts));
		// ---- centre cluster ----
		{
			int ccx = tvw / 2;
			int top = clY + Math.round((1f - open) * -14f);
			int bw0 = Brand.boltWidth(2);
			Brand.bolt(ctx, ccx, top, 2, open, now);
			ui.spacedCenter("RAY CLIENT", ccx, top + 48, Theme.fade(0xFFFFFFFF, open), 3);
			int by2 = top + 64, mw = 92, gw = 26, gap2 = 6;
			int bx2 = ccx - (mw + gap2 + gw) / 2;
			ui.pill(bx2, by2, mw, 26, "MODS", accent, true, () -> openMods("ALL", null));
			boolean gh = ui.over(bx2 + mw + gap2, by2, gw, 26);
			float gv = ui.anim("hudgear", gh);
			ui.rrectGrad(bx2 + mw + gap2, by2, gw, 26, 6, Theme.mix(0xFF1B1F26, 0xFF2A303B, gv), Theme.mix(0xFF14171D, 0xFF1F242C, gv));
			ui.ring(bx2 + mw + gap2, by2, gw, 26, 6, Theme.mix(Theme.STROKE, accent, gv * 0.8f));
			ui.gearIcon(bx2 + mw + gap2 + 10, by2 + 10, Theme.mix(Theme.DIM, 0xFFFFFFFF, gv));
			ui.click("hudsettings", bx2 + mw + gap2, by2, gw, 26, () -> openMods("ALL", "SETTINGS"));
		}

		int ty0 = tbY + Math.round((1f - open) * 30f);
		ui.shadow(tbX, ty0, tbW, tbH, 10);
		ui.panelGrad(tbX, ty0, tbW, tbH, 10, 0xF4171A20, 0xF4101217, Theme.STROKE_HI);
		ui.rect(tbX + 14, ty0 + 1, tbW - 28, 1, Theme.fade(0xFFFFFFFF, 0.07f));
		int bx = tbX + 10, by = ty0 + 10, bh = 34, gap = 6;
		int free = tbW - 20 - gap * 3;
		int w2 = free * 22 / 100, w3 = free * 24 / 100, w4 = free * 34 / 100, w5 = free - w2 - w3 - w4;
		ui.pill(bx, by, w2, bh, "VISUALS", 0xFF8FA3C8, true, () -> openMods("VISUAL", null));
		bx += w2 + gap;
		ui.pill(bx, by, w3, bh, "PROFILES", 0xFF8FA3C8, true, () -> openMods("ALL", "PROFILES"));
		bx += w3 + gap;
		ui.pill(bx, by, w4, bh, "AUTO ARRANGE", 0xFF8FA3C8, true, () -> autoArrange(active));
		bx += w4 + gap;
		ui.pill(bx, by, w5, bh, "RESET", 0xFFE0384A, true, () -> {
			cfg.positions.clear();
			RayConfig.save();
			Toasts.push("HUD positions reset");
		});
		ui.poll();
		ctx.getMatrices().popMatrix();
		boolean uiClicked = ui.leftPressed && overToolbar;

		// ---- mouse input (polled) ----
		long win = client.getWindow().getHandle();
		boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean rdown = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

		if (rdown && !rightWasDown && dragging == null && !overToolbar) {
			for (int i = active.size() - 1; i >= 0; i--) {
				HudModule m = active.get(i);
				if (inside(mouseX, mouseY, m.x, m.y, m.ew(), m.eh())) {
					rightWasDown = rdown;
					mouseWasDown = down;
					client.setScreen(new ModuleSettingsScreen(this, m));
					return;
				}
			}
		}
		rightWasDown = rdown;

		if (down && !mouseWasDown && !overToolbar && !uiClicked) {
			for (int i = active.size() - 1; i >= 0; i--) {
				HudModule m = active.get(i);
				if (inside(mouseX, mouseY, m.x, m.y, m.ew(), m.eh())) {
					dragging = m;
					offX = mouseX - m.x;
					offY = mouseY - m.y;
					break;
				}
			}
		}
		if (down && dragging != null) {
			int nx = mouseX - offX;
			int ny = mouseY - offY;
			int ew = dragging.ew(), eh = dragging.eh();
			guideX = -1;
			guideY = -1;
			// screen edges and centre
			if (Math.abs(nx - 4) < 6) { nx = 4; guideX = 4; }
			if (Math.abs(nx + ew - (width - 4)) < 6) { nx = width - 4 - ew; guideX = width - 4; }
			if (Math.abs(ny - 4) < 6) { ny = 4; guideY = 4; }
			if (Math.abs(ny + eh - (height - 4)) < 6) { ny = height - 4 - eh; guideY = height - 4; }
			if (Math.abs(nx + ew / 2 - width / 2) < 5) { nx = width / 2 - ew / 2; guideX = width / 2; }
			if (Math.abs(ny + eh / 2 - height / 2) < 5) { ny = height / 2 - eh / 2; guideY = height / 2; }
			// other modules: align edges or sit right next to them
			int bestDx = 5, bestDy = 5, sx = nx, sy = ny;
			for (HudModule o : active) {
				if (o == dragging) continue;
				int[] xs = {o.x, o.x + o.ew() - ew, o.x + o.ew() + 2, o.x - ew - 2};
				int[] gxs = {o.x, o.x + o.ew(), o.x + o.ew(), o.x};
				for (int k = 0; k < xs.length; k++) {
					if (Math.abs(nx - xs[k]) < bestDx) { bestDx = Math.abs(nx - xs[k]); sx = xs[k]; guideX = gxs[k]; }
				}
				int[] ys = {o.y, o.y + o.eh() - eh, o.y + o.eh() + 2, o.y - eh - 2};
				int[] gys = {o.y, o.y + o.eh(), o.y + o.eh(), o.y};
				for (int k = 0; k < ys.length; k++) {
					if (Math.abs(ny - ys[k]) < bestDy) { bestDy = Math.abs(ny - ys[k]); sy = ys[k]; guideY = gys[k]; }
				}
			}
			dragging.moveTo(sx, sy, width, height);
		}
		if (!down && dragging != null) {
			dragging = null;
			guideX = -1;
			guideY = -1;
			RayConfig.save();
		}
		mouseWasDown = down;
	}
}
