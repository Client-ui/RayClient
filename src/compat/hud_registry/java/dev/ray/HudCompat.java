package dev.ray;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.function.BiConsumer;

/** Minecraft 1.21.6 and newer: Fabric API's HUD element registry. */
final class HudCompat {
	private HudCompat() {}

	private static void add(String id, String name, BiConsumer<DrawContext, Tick> element) {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(RayClient.MOD_ID, id),
			(ctx, tc) -> Safe.render(name, element, ctx, TickCompat.of(tc)));
	}

	static void register() {
		add("hud", "HUD", Hud::render);
		add("lyrics", "Lyric Sync", LyricsHud::render);
		add("outline", "Block Outline", BlockOutline::render);
		add("waypoints", "Waypoints", WaypointMarkers::render);
		add("hitboxes", "Hitboxes", Hitboxes::render);
		add("toasts", "Notifications", Toasts::render);
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (ctx, tickCounter) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (HudLayers.customCrosshair()) {
				Hud.drawCrosshair(ctx, mc);
			} else {
				original.render(ctx, tickCounter);
			}
		});
		try {
			HudElementRegistry.replaceElement(Identifier.of("minecraft", "scoreboard"), original -> (ctx, tickCounter) -> {
				if (HudLayers.scoreboardHidden()) return;
				boolean pushed = HudLayers.scoreboardBegin(ctx);
				original.render(ctx, tickCounter);
				HudLayers.scoreboardEnd(ctx, pushed);
			});
		} catch (Throwable t) {
			System.err.println("[Ray Client] Could not hook the scoreboard: " + t);
		}
	}
}
