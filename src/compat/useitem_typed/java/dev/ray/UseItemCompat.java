package dev.ray;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.util.TypedActionResult;

/** Minecraft 1.21.1 and older: UseItemCallback returns a TypedActionResult. */
final class UseItemCompat {
	private UseItemCompat() {}

	static void register() {
		UseItemCallback.EVENT.register((player, world, hand) -> {
			CombatTracker.onUseItem(player, hand);
			return TypedActionResult.pass(player.getStackInHand(hand));
		});
	}
}
