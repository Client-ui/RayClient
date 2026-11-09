package dev.ray.mixin;

import dev.ray.RayConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Viewmodel: move and scale the first-person held item.
 * Side Shield: move the shield out of the middle of the screen.
 *
 * Why the old Side Shield misbehaved:
 *  - it waited for player.isBlocking(), which only turns true a few ticks AFTER you start raising the shield, so the
 *    shield first showed up in the middle and then snapped sideways;
 *  - it flipped instantly between the two positions (no animation);
 *  - the hand was guessed in a way that could pick the wrong item for left-handed players.
 *
 * Now "raised" comes from the real item use (active hand + shield in that hand), and the offset is eased in and out
 * per frame, so the shield glides to the side and back instead of jumping.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
	/** Eased 0..1 amount of the side offset for [right arm, left arm]. */
	private static final float[] RAY$SIDE = {0f, 0f};
	private static final long[] RAY$CLOCK = {0L, 0L};

	@Inject(method = "applyEquipOffset", at = @At("RETURN"), require = 0)
	private void ray$viewmodel(MatrixStack matrices, Arm arm, float equipProgress, CallbackInfo ci) {
		RayConfig cfg = RayConfig.get();
		float side = arm == Arm.RIGHT ? 1.0f : -1.0f;

		if (cfg.viewmodel) {
			matrices.translate(side * cfg.vmX / 100.0f, cfg.vmY / 100.0f, cfg.vmZ / 100.0f);
			float s = cfg.vmScale / 100.0f;
			matrices.scale(s, s, s);
		}

		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		int idx = arm == Arm.RIGHT ? 0 : 1;
		if (player == null) {
			RAY$SIDE[idx] = 0f;
			return;
		}

		// the main arm holds the main hand, the other arm the off hand
		Hand hand = arm == player.getMainArm() ? Hand.MAIN_HAND : Hand.OFF_HAND;
		boolean shield = cfg.sideShield && player.getStackInHand(hand).isOf(Items.SHIELD);
		dev.ray.SideShield.hooked = true;
		if (!shield) {
			// not a shield (any more): forget the old offset, so a shield picked up later does not start offset and glide back
			RAY$SIDE[idx] = 0f;
			RAY$CLOCK[idx] = 0L;
			return;
		}
		boolean raised = player.isUsingItem() && player.getActiveHand() == hand;
		float target = !cfg.ssOnlyBlocking || raised ? 1f : 0f;
		if (raised && !dev.ray.SideShield.announced) {
			// one-time proof that this code runs on the player's Minecraft version
			dev.ray.SideShield.announced = true;
			dev.ray.Toasts.push("Side Shield is working: x " + cfg.ssX + "  y " + cfg.ssY + "  size " + cfg.ssScale + "%");
		}

		// ease toward the target, with a separate clock per arm (an empty hand may not be processed at all)
		long now = System.nanoTime();
		float dt = RAY$CLOCK[idx] == 0L ? 0.016f : Math.min(0.1f, (now - RAY$CLOCK[idx]) / 1.0e9f);
		RAY$CLOCK[idx] = now;
		float cur = RAY$SIDE[idx];
		if (cfg.ssSmooth) {
			cur += (target - cur) * (1.0f - (float) Math.exp(-dt * 16.0f));
			if (Math.abs(target - cur) < 0.002f) cur = target;
		} else {
			cur = target;
		}
		RAY$SIDE[idx] = cur;
		if (cur <= 0.001f) return;

		matrices.translate(side * cfg.ssX / 100.0f * cur, cfg.ssY / 100.0f * cur, cfg.ssZ / 100.0f * cur);
		if (cfg.ssTilt != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * cfg.ssTilt * cur));
		float s = 1.0f + (cfg.ssScale / 100.0f - 1.0f) * cur;
		matrices.scale(s, s, s);
	}
}
