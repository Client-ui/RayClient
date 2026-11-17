package dev.ray;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** Minecraft 1.20 to 1.20.6: one HUD callback that receives the tick delta as a float (the vanilla crosshair and scoreboard are handled by InGameHudLegacyMixin). */
final class HudCompat {
	private HudCompat() {}

	static void register() {
		HudRenderCallback.EVENT.register((ctx, tickDelta) -> HudLayers.renderAll(ctx, new Tick(tickDelta)));
	}
}
