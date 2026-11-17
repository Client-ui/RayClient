package dev.ray.mixin;

import dev.ray.RayConfig;
import dev.ray.ZoomState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the smooth zoom to the world field of view, every frame. */
@Mixin(GameRenderer.class)
public class GameRendererFovMixin {
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void ray$zoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable cir) {
		ZoomState.hooked = true;
		dev.ray.Stretch.worldCall = changingFov;
		if (!changingFov) return; // the hand uses the non-changing call; leave it alone
		float amount = ZoomState.update();
		if (amount <= 0f || !RayConfig.get().zoomEnabled) return;
		double base = MinecraftClient.getInstance().options.getFov().getValue();
		float factor = (float) ZoomState.fovFactor(base, amount);
		// getFov returns a float on 1.21.2 and newer, a double on older versions
		Object v = cir.getReturnValue();
		if (v instanceof Float f) cir.setReturnValue(f * factor);
		else if (v instanceof Double d) cir.setReturnValue(d * (double) factor);
	}
}
