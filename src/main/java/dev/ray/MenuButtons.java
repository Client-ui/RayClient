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
		int r = 7;
		if (hero) {
			// a quiet dark card with the logo; the accent only appears on hover
			UI.rrect(x, y, w, h, r, Theme.mix(Theme.CARD, Theme.CARD_HOVER, hv));
			UI.ring(x, y, w, h, r, Theme.mix(Theme.LINE, accent, Math.max(hv, fv)));
			int lh = Math.max(12, h - 12);
			Logo.draw(ctx, x + 10, y + (h - lh) / 2, lh, 1f);
			UI.headText("Ray Client", x + 14 + Logo.width(lh) + 6, y + (h - 8) / 2 + 1, 0xFFFFFFFF);
			UI.chevron(x + w - 14 + Math.round(hv * 2f), y + h / 2 - 2, Theme.mix(Theme.MUTE, accent, hv));
			UI.right("HUD & Mods", x + w - 26, y + (h - 8) / 2 + 1, Theme.mix(Theme.DIM, 0xFFFFFFFF, hv));
			return true;
		}
		String t = label.getString();
		if (pause && active && t.equals("Back to Game")) {
			// the main action: the launcher's solid orange
			UI.rrect(x, y, w, h, r, Theme.mix(accent, 0xFFFFFFFF, 0.10f * hv));
			UI.headCenter(text, x + w / 2, y + (h - 8) / 2 + 1, Ui.onAccent());
			return true;
		}
		if (pause && active && (t.startsWith("Save and Quit") || t.startsWith("Disconnect") || t.startsWith("Quit"))) {
			// leaving: quiet until you point at it, then red
			UI.rrect(x, y, w, h, r, Theme.mix(Theme.CARD, Theme.fade(Theme.BAD, 0.20f), hv));
			UI.ring(x, y, w, h, r, Theme.mix(Theme.LINE, Theme.fade(Theme.BAD, 0.7f), hv));
			UI.center(text, x + w / 2, y + (h - 8) / 2 + 1, Theme.mix(0xFFFF8A94, 0xFFFFFFFF, hv * 0.6f));
			return true;
		}
		UI.rrect(x, y, w, h, r, active ? Theme.mix(Theme.CARD, Theme.CARD_HOVER, hv) : 0xFF0F0E12);
		if (!pause) UI.ring(x, y, w, h, r, Theme.mix(Theme.LINE, 0xFF3A3542, active ? hv : 0f));
		if (active && fv > 0.02f) UI.ring(x, y, w, h, r, Theme.fade(accent, 0.8f * fv));
		int tc = active ? Theme.mix(0xFFE4E4E8, 0xFFFFFFFF, hv) : Theme.MUTE;
		if (pause) {
			// the pause menu is a list: text on the left, a small arrow on the right
			UI.text(text, x + 12, y + (h - 8) / 2 + 1, tc);
			if (active && UI.w(text) + 34 < w) UI.chevron(x + w - 14 + Math.round(hv * 2f), y + h / 2 - 2, Theme.mix(Theme.MUTE, accent, hv));
		} else {
			UI.center(text, x + w / 2, y + (h - 8) / 2 + 1, tc);
		}
		return true;
	}
}
