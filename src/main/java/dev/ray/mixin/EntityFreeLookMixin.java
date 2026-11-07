package dev.ray.mixin;

import dev.ray.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While free look is active, mouse movement turns the camera instead of the player. */
@Mixin(Entity.class)
public class EntityFreeLookMixin {
	@Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
	private void ray$freeLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
		if (FreeLook.active && FreeLook.hooked && (Object) this == MinecraftClient.getInstance().player) {
			FreeLook.rotate(cursorDeltaX, cursorDeltaY);
			ci.cancel();
		}
	}
}
