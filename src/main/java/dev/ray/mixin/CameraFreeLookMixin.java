package dev.ray.mixin;

import dev.ray.FreeLook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** While free look is active, the camera uses the free-look angles instead of the player's. */
@Mixin(Camera.class)
public class CameraFreeLookMixin {
	@ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
	private float ray$yaw(float yaw) {
		FreeLook.hooked = true;
		FreeLook.frame();
		return FreeLook.active ? FreeLook.yaw : yaw;
	}

	@ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 1, argsOnly = true, require = 0)
	private float ray$pitch(float pitch) {
		FreeLook.frame();
		return FreeLook.active ? FreeLook.pitch : pitch;
	}
}
