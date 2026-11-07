package dev.ray;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD editor: drag modules (they snap to screen edges and to each other), right-click one for its settings,
 * "Auto Arrange" lays everything out in non-overlapping columns, "Mods" opens the module list.
 */
public class HudEditorScreen extends Screen {
	private HudModule dragging = null;
	private int offX, offY;
	private boolean mouseWasDown = true, rightWasDown = true;

	public HudEditorScreen() {
		super(Text.literal("HUD Editor"));
	}

	@Override
	protected void init() {
		mouseWasDown = true;
		rightWasDown = true;
		dragging = null;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private static boolean inside(int mx, int my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static void border(DrawContext c, int x, int y, int w, int h, int col) {
		c.fill(x, y, x + w, y + 1, col);
		c.fill(x, y + h - 1, x + w, y + h, col);
		c.fill(x, y, x + 1, y + h, col);
		c.fill(x + w - 1, y, x + w, y + h, col);
	}

	private final Ui ui = new Ui();

	private boolean button(DrawContext ctx, int mx, int my, int x, int y, int w, int h, String label, int color) {
		boolean hov = inside(mx, my, x, y, w, h);
		ui.ctx = ctx;
		ui.tr = textRenderer;
		ui.panel(x, y, w, h, 3, hov ? Theme.mix(0xFF12162A, color, 0.55f) : Theme.mix(0xFF0F1220, color, 0.25f), hov ? color : Theme.STROKE);
		ClientFont.draw(ctx, textRenderer, label, x + (w - ClientFont.width(textRenderer, label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
		return hov;
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
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		RayConfig cfg = RayConfig.get();
		int accent = Theme.accent();
		ctx.fill(0, 0, width, height, 0x66000000);

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
		for (HudModule m : active) {
			m.render(ctx, client, textRenderer);
			boolean hot = m == dragging || (dragging == null && inside(mouseX, mouseY, m.x, m.y, m.ew(), m.eh()));
			border(ctx, m.x - 1, m.y - 1, m.ew() + 2, m.eh() + 2, hot ? 0xFFFFFFFF : Theme.withAlpha(accent, 0xAA));
		}

		String title = "HUD Editor";
		String hint = "Drag to move  -  Right-click a module for its settings  -  ESC to close";
		ClientFont.draw(ctx, textRenderer, title, (width - ClientFont.width(textRenderer, title)) / 2, 10, 0xFFFFFFFF, true);
		ctx.fill(width / 2 - 14, 21, width / 2 + 14, 23, accent);
		ClientFont.draw(ctx, textRenderer, hint, (width - ClientFont.width(textRenderer, hint)) / 2, 28, 0xFFB0BCD6, false);

		int bx = width / 2 - 62, by = height / 2 - 44;
		ui.ctx = ctx;
		ui.tr = textRenderer;
		ui.panel(bx - 8, by - 8, 140, 110, 5, 0xFF0C0F18, Theme.fade(accent, 0.55f));
		ui.rect(bx - 7, by - 7, 138, 2, accent);
		boolean hovMods = button(ctx, mouseX, mouseY, bx, by, 124, 24, "MODS MENU", accent);
		boolean hovVis = button(ctx, mouseX, mouseY, bx, by + 28, 60, 18, "Visuals", 0xFF5A6BA0);
		boolean hovProf = button(ctx, mouseX, mouseY, bx + 64, by + 28, 60, 18, "Profiles", 0xFF5A6BA0);
		boolean hovArr = button(ctx, mouseX, mouseY, bx, by + 50, 124, 18, "Auto Arrange", 0xFF5A6BA0);
		boolean hovReset = button(ctx, mouseX, mouseY, bx, by + 72, 124, 18, "Reset Positions", 0xFF9A3B45);

		// ---- mouse input (polled) ----
		long win = client.getWindow().getHandle();
		boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		boolean rdown = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

		if (rdown && !rightWasDown && dragging == null) {
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

		if (down && !mouseWasDown) {
			if (hovMods || hovVis || hovProf) {
				mouseWasDown = down;
				RayScreen.selectPage("MODS");
				if (hovVis) RayScreen.selectFilter("VISUAL");
				else RayScreen.selectFilter("ALL");
				if (hovProf) RayScreen.selectPage("PROFILES");
				client.setScreen(new RayScreen(this));
				return;
			} else if (hovArr) {
				autoArrange(active);
			} else if (hovReset) {
				cfg.positions.clear();
				RayConfig.save();
			} else {
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
		}
		if (down && dragging != null) {
			int nx = mouseX - offX;
			int ny = mouseY - offY;
			int ew = dragging.ew(), eh = dragging.eh();
			// screen edges and centre
			if (Math.abs(nx - 4) < 6) nx = 4;
			if (Math.abs(nx + ew - (width - 4)) < 6) nx = width - 4 - ew;
			if (Math.abs(ny - 4) < 6) ny = 4;
			if (Math.abs(ny + eh - (height - 4)) < 6) ny = height - 4 - eh;
			if (Math.abs(nx + ew / 2 - width / 2) < 5) nx = width / 2 - ew / 2;
			// other modules: align edges or sit right next to them
			int bestDx = 5, bestDy = 5, sx = nx, sy = ny;
			for (HudModule o : active) {
				if (o == dragging) continue;
				int[] xs = {o.x, o.x + o.ew() - ew, o.x + o.ew() + 2, o.x - ew - 2};
				for (int c : xs) {
					if (Math.abs(nx - c) < bestDx) { bestDx = Math.abs(nx - c); sx = c; }
				}
				int[] ys = {o.y, o.y + o.eh() - eh, o.y + o.eh() + 2, o.y - eh - 2};
				for (int c : ys) {
					if (Math.abs(ny - c) < bestDy) { bestDy = Math.abs(ny - c); sy = c; }
				}
			}
			dragging.moveTo(sx, sy, width, height);
		}
		if (!down && dragging != null) {
			dragging = null;
			RayConfig.save();
		}
		mouseWasDown = down;
	}
}
