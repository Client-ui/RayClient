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
		int r = Math.min(h / 2, 8);
		if (active && hv > 0.02f) UI.glow(x, y, w, h, r, accent, hv * 0.8f);
		int top = active ? Theme.mix(0xFF1A1F34, accent, 0.45f * hv) : 0xFF12151F;
		int bot = active ? Theme.mix(0xFF0E1224, accent, 0.22f * hv) : 0xFF0C0F18;
		UI.rrectGrad(x, y, w, h, r, top, bot);
		UI.ring(x, y, w, h, r, Theme.mix(focused && active ? accent : 0xFF2A3047, accent, hv));
		int tw = mc.textRenderer.getWidth(label);
		ctx.drawText(mc.textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2, active ? Theme.mix(0xFFD6DBEA, 0xFFFFFFFF, hv) : 0xFF5C657D, false);
		return true;
	}
}
