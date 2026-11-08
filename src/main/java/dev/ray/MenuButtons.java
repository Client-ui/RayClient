package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/** Draws Minecraft's own buttons in the Ray style (only in menus outside a world, so in-game screens stay vanilla). */
public final class MenuButtons {
	private static final Ui UI = new Ui();
	private static final Map<Integer, String> IDS = new HashMap<>();

	private MenuButtons() {}

	/** Returns true when the button was drawn (the vanilla drawing is then skipped). */
	public static boolean draw(DrawContext ctx, int x, int y, int w, int h, Text label, boolean active, boolean hovered, boolean focused, int key) {
		MinecraftClient mc = MinecraftClient.getInstance();
		RayConfig cfg = RayConfig.get();
		if (mc.world != null || !cfg.styledMenuButtons || w < 8 || h < 8) return false;
		if (IDS.size() > 256) IDS.clear();
		String id = IDS.computeIfAbsent(key, k -> "mb" + k);
		UI.begin(ctx, mc.textRenderer, -1, -1);
		int accent = Theme.accent();
		float hv = UI.anim(id, hovered && active);
		float fv = UI.anim(id + "f", focused && active);
		int r = Math.min(h / 2, 6);
		if (active && hv > 0.02f) UI.glow(x, y, w, h, r, accent, hv * 0.7f);
		int top = active ? Theme.mix(0xFF1C2027, Theme.mix(0xFF1C2027, accent, 0.36f), hv) : 0xFF101217;
		int bot = active ? Theme.mix(0xFF13161B, Theme.mix(0xFF13161B, accent, 0.24f), hv) : 0xFF0E1014;
		UI.rrectGrad(x, y, w, h, r, top, bot);
		if (active) ctx.fill(x + r, y + 1, x + w - r, y + 2, Theme.fade(0xFFFFFFFF, 0.05f + 0.07f * hv));
		UI.ring(x, y, w, h, r, active ? Theme.mix(Theme.mix(Theme.STROKE, accent, fv * 0.8f), accent, hv * 0.9f) : 0xFF1B1F26);
		int tw = mc.textRenderer.getWidth(label);
		ctx.drawText(mc.textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2 + 1, active ? Theme.mix(0xFFD9DEEA, 0xFFFFFFFF, hv) : 0xFF5C657D, false);
		return true;
	}
}
