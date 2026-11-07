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
		int r = Math.min(h / 2, 5);
		int fill = active ? Theme.mix(0xFF14171D, Theme.mix(0xFF14171D, accent, 0.30f), hv) : 0xFF0F1116;
		UI.rrect(x, y, w, h, r, fill);
		UI.ring(x, y, w, h, r, active ? Theme.mix(focused ? accent : Theme.STROKE, accent, hv * 0.85f) : 0xFF1C2028);
		int tw = mc.textRenderer.getWidth(label);
		ctx.drawText(mc.textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2, active ? Theme.mix(0xFFD6DBEA, 0xFFFFFFFF, hv) : 0xFF5C657D, false);
		return true;
	}
}
