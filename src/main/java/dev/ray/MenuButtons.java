package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws Minecraft's own buttons in the Ray style: frosted glass, accent glow on hover, the client font. It is used on
 * the menus outside a world and on the pause (Esc) menu; every other in-game screen stays vanilla. The "Ray Client"
 * button of the pause menu gets a lit accent look with the logo.
 */
public final class MenuButtons {
	private static final Ui UI = new Ui();
	private static final Map<Integer, String> IDS = new HashMap<>();

	private MenuButtons() {}

	/** Returns true when the button was drawn (the vanilla drawing is then skipped). */
	public static boolean draw(DrawContext ctx, int x, int y, int w, int h, Text label, boolean active, boolean hovered, boolean focused, int key) {
		MinecraftClient mc = MinecraftClient.getInstance();
		RayConfig cfg = RayConfig.get();
		boolean pause = mc.currentScreen instanceof GameMenuScreen;
		if ((mc.world != null && !pause) || !cfg.styledMenuButtons || w < 8 || h < 8) return false;
		if (IDS.size() > 256) IDS.clear();
		String id = IDS.computeIfAbsent(key, k -> "mb" + k);
		UI.begin(ctx, mc.textRenderer, -1, -1);
		int accent = Theme.accent();
		String text = label.getString();
		boolean hero = pause && text.startsWith("Ray Client");
		float hv = UI.anim(id, hovered && active);
		float fv = UI.anim(id + "f", focused && active);
		int r = Math.min(h / 2, 9);
		if (hero) {
			UI.accentFill(x, y, w, h, r, hv);
			int lh = Math.max(12, h - 10);
			Logo.draw(ctx, x + 10, y + (h - lh) / 2, lh, 1f);
			UI.headCenter("Ray Client", x + w / 2, y + (h - 8) / 2 + 1, Ui.onAccent());
			UI.right("HUD & Mods", x + w - 10, y + (h - 8) / 2 + 1, Theme.fade(Ui.onAccent(), 0.75f));
			return true;
		}
		UI.rrect(x, y, w, h, r, active ? Ui.white(0.08f + 0.07f * hv) : Ui.white(0.035f));
		if (active && hv > 0.05f) UI.ring(x, y, w, h, r, Ui.white(0.18f * hv));
		if (active && fv > 0.02f) UI.ring(x, y, w, h, r, Theme.fade(accent, 0.8f * fv));
		UI.center(text, x + w / 2, y + (h - 8) / 2 + 1, active ? Theme.mix(0xFFE0E0EA, 0xFFFFFFFF, hv) : Theme.MUTE);
		return true;
	}
}
