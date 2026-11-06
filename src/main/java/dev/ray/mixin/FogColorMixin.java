package dev.ray.mixin;

import dev.ray.Sky;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom Sky: recolours the horizon fog so the ground fades into the new sky instead of into vanilla blue.
 * Targeted by name so the project still compiles if Mojang moves this class; skipped silently if it is not found.
 */
@Mixin(targets = "net.minecraft.client.render.fog.FogRenderer")
public abstract class FogColorMixin {
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true, require = 0)
	private void ray$fog(Camera camera, float tickProgress, ClientWorld world, int viewDistance, float skyDarkness, CallbackInfoReturnable cir) {
		if (!Sky.active(world)) return;
		Object v = cir.getReturnValue();
		if (v instanceof Vector4f f) {
			float[] c = Sky.fog(f.x, f.y, f.z);
			cir.setReturnValue(new Vector4f(c[0], c[1], c[2], f.w));
		}
	}
}
