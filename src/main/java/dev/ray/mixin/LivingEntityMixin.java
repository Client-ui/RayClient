package dev.ray.mixin;

import dev.ray.RayConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Animation mod: custom swing duration for the local player (visual only). */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
	private void ray$swingDuration(CallbackInfoReturnable<Integer> cir) {
		RayConfig cfg = RayConfig.get();
		if (!cfg.customSwing) return;
		if ((Object) this != MinecraftClient.getInstance().player) return;
		cir.setReturnValue(cfg.swingDuration);
	}
}
