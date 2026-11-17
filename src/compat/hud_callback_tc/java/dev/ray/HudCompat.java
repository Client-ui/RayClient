package dev.ray;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/** Minecraft 1.21 to 1.21.5: one HUD callback that runs after the vanilla HUD (the vanilla crosshair and scoreboard are handled by InGameHudLegacyMixin). */
final class HudCompat {
	private HudCompat() {}

	static void register() {
		HudRenderCallback.EVENT.register((ctx, tc) -> HudLayers.renderAll(ctx, TickCompat.of(tc)));
	}
}
