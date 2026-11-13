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

	/** A small round corner button on a module while editing: a gear (settings) or a minus (turn off). */
	private void corner(DrawContext ctx, int cx, int cy, int color, boolean minus, boolean hover) {
		ui.circle(cx, cy, 6, hover ? Theme.mix(color, 0xFFFFFFFF, 0.28f) : color);
		if (minus) ctx.fill(cx - 3, cy, cx + 3, cy + 1, 0xFFFFFFFF);
		else ui.gearIcon(cx - 3, cy - 3, 0xFFFFFFFF);
	}

	private void openMods(String filter, String pageName) {
		RayScreen.selectPage("MODS");
		RayScreen.selectFilter(filter);
		if (pageName != null) RayScreen.selectPage(pageName);
		client.setScreen(new RayScreen(this));
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		ClientFont.scale = 1f;
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
		// the centre cluster (logo and one settings button), in virtual units of the same scale
		int clW = 176, clH = 84;
		int clX = (tvw - clW) / 2, clY = (tvh - clH) / 2;
		int crx = Math.round(clX * ts), cry = Math.round(clY * ts), crw = Math.round(clW * ts), crh = Math.round(clH * ts);
		boolean overToolbar = inside(mouseX, mouseY, crx, cry, crw, crh);

		ui.begin(ctx, textRenderer, mouseX, mouseY);
		HudModule hotModule = null;
		for (HudModule m : active) {
			m.render(ctx, client, textRenderer);
			int mw = m.ew(), mh = m.eh();
			boolean hot = m == dragging || (dragging == null && !overToolbar && inside(mouseX, mouseY, m.x - 6, m.y - 2, mw + 12, mh + 9));
			float hv = ui.anim("hm" + m.id, hot);
			if (hv > 0.02f) ctx.fill(m.x, m.y, m.x + mw, m.y + mh, Theme.fade(0xFFFFFFFF, 0.07f * hv));
			ui.ring(m.x - 2, m.y - 2, mw + 4, mh + 4, 3, Theme.mix(Ui.white(0.28f), 0xFFFFFFFF, hv));
			// under the cursor: the module's name sits under it (above it at the bottom edge), with a settings (accent)
			// button bottom-left and a turn-off (red) button bottom-right. Only the hovered module shows them, so closely
			// stacked modules never cover each other with labels.
			if (hot) hotModule = m;
			if (m == dragging) {
				String tag = m.x + ", " + m.y;
				int tw = ui.w(tag) + 12;
				int tx = Math.max(2, Math.min(width - tw - 2, m.x));
				int ty = m.y - 18 >= 2 ? m.y - 18 : m.y + mh + 4;
				ui.rrect(tx, ty, tw, 14, 5, 0xF0101014);
				ui.ring(tx, ty, tw, 14, 5, Ui.white(0.14f));
				ui.text(tag, tx + 6, ty + 3, 0xFFFFFFFF);
			}
		}
		if (hotModule != null) { // drawn last so it sits on top of the modules around it
			HudModule m = hotModule;
			int mw = m.ew(), mh = m.eh();
			int nameY = m.y + mh + 16 <= height ? m.y + mh + 9 : m.y - 12;
			ClientFont.draw(ctx, textRenderer, m.name, m.x, nameY, 0xFFFFFFFF, true);
			corner(ctx, m.x + 2, m.y + mh, Theme.accent(), false, inside(mouseX, mouseY, m.x - 5, m.y + mh - 7, 14, 14));
			corner(ctx, m.x + mw - 2, m.y + mh, Theme.BAD, true, inside(mouseX, mouseY, m.x + mw - 9, m.y + mh - 7, 14, 14));
		}
		// guide lines while dragging
		if (dragging != null) {
			if (guideX >= 0) ctx.fill(guideX, 0, guideX + 1, height, Theme.fade(accent, 0.65f));
			if (guideY >= 0) ctx.fill(0, guideY, width, guideY + 1, Theme.fade(accent, 0.65f));
		}

		// one quiet line of help at the top
		String hint = "Drag to move   -   Right-click for settings   -   ESC to close";
		ClientFont.draw(ctx, textRenderer, hint, (width - ui.w(hint)) / 2, 10 - Math.round((1f - open) * 14f), Theme.fade(0xFFFFFFFF, 0.7f * open), true);

		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(ts, ts);
		ClientFont.scale = ts;
		ui.begin(ctx, textRenderer, UiScale.mouse(mouseX, ts), UiScale.mouse(mouseY, ts));
		{
			int ccx = tvw / 2;
			int top = clY + Math.round((1f - open) * -14f);
			int lh = 46;
			Brand.logo(ctx, ccx, top, lh, open, now);

			// one dark settings button, like a game's own menu button
			int bw = 124, bh2 = 26, bx2 = ccx - bw / 2, by2 = top + lh + 12;
			float bv = ui.anim("hudmain", ui.over(bx2, by2, bw, bh2));
			ui.rrect(bx2, by2, bw, bh2, 5, Theme.mix(0xF0101014, 0xF01C1C22, bv));
			ui.ring(bx2, by2, bw, bh2, 5, Ui.white(0.10f + 0.10f * bv));
			String lbl = "RAY SETTINGS";
			ui.spaced(lbl, ccx - ui.spacedWidth(lbl, 2) / 2, by2 + 9, 0xFFFFFFFF, 2);
			ui.click("hudmain", bx2, by2, bw, bh2, () -> openMods("ALL", null));

		}

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
				if (!inside(mouseX, mouseY, m.x - 6, m.y - 2, m.ew() + 12, m.eh() + 9)) continue;
				if (inside(mouseX, mouseY, m.x - 5, m.y + m.eh() - 7, 14, 14)) {
					mouseWasDown = down;
					client.setScreen(new ModuleSettingsScreen(this, m));
					return;
				}
				if (inside(mouseX, mouseY, m.x + m.ew() - 9, m.y + m.eh() - 7, 14, 14)) {
					mouseWasDown = down;
					m.setEnabled(false);
					RayConfig.save();
					Toasts.push(m.name + " turned off");
					return;
				}
			}
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
