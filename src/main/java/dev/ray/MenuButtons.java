package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * Draws Minecraft's own buttons in the Ray style: glass surface, accent border on hover, the client font. It is used on
 * the menus outside a world and on the pause (Esc) menu; every other in-game screen stays vanilla. The "Ray Client"
 * button of the pause menu gets a lit accent look with a bolt.
 */
public final class MenuButtons {
	private static final Ui UI = new Ui();
	private static final Map<Integer, String> IDS = new HashMap<>();
	private static final String[] BOLT = {"..##", ".##.", "###.", ".###", "..#."};

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
		int r = Math.min(h / 2, 6);
		if (hero) {
			UI.glow(x, y, w, h, r, accent, 0.5f + 0.5f * hv);
			UI.rrectGrad(x, y, w, h, r, Theme.mix(accent, 0xFFFFFFFF, 0.10f + 0.12f * hv), Theme.mix(accent, 0xFF000000, 0.42f - 0.14f * hv));
			UI.ring(x, y, w, h, r, Theme.fade(Theme.mix(accent, 0xFFFFFFFF, 0.5f), 0.8f));
			ctx.fill(x + r, y + 1, x + w - r, y + 2, 0x44FFFFFF);
			UI.pix(BOLT, x + 10, y + (h - 10) / 2, 2, 0xFFFFFFFF);
			UI.spacedCenter("RAY CLIENT", x + w / 2, y + (h - 8) / 2 + 1, 0xFFFFFFFF, 2);
			UI.right("HUD & MODS", x + w - 10, y + (h - 8) / 2 + 1, 0xCCFFFFFF);
			return true;
		}
		if (active && hv > 0.02f) UI.glow(x, y, w, h, r, accent, hv * 0.7f);
		int top = active ? Theme.mix(0xFF1C2027, Theme.mix(0xFF1C2027, accent, 0.36f), hv) : 0xFF101217;
		int bot = active ? Theme.mix(0xFF13161B, Theme.mix(0xFF13161B, accent, 0.24f), hv) : 0xFF0E1014;
		UI.rrectGrad(x, y, w, h, r, top, bot);
		if (active) ctx.fill(x + r, y + 1, x + w - r, y + 2, Theme.fade(0xFFFFFFFF, 0.05f + 0.07f * hv));
		UI.ring(x, y, w, h, r, active ? Theme.mix(Theme.mix(Theme.STROKE, accent, fv * 0.8f), accent, hv * 0.9f) : 0xFF1B1F26);
		UI.center(text, x + w / 2, y + (h - 8) / 2 + 1, active ? Theme.mix(0xFFD9DEEA, 0xFFFFFFFF, hv) : 0xFF5C657D);
		return true;
	}
}
