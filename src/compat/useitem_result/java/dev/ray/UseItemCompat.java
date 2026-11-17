package dev.ray;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.ActionResult;

/** Minecraft 1.21.2 and newer: UseItemCallback returns an ActionResult. */
final class UseItemCompat {
	private UseItemCompat() {}

	static void register() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			CombatTracker.onUseItem(player, hand);
			return ActionResult.PASS;
		});
	}
}
