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
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void ray$zoom(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		ZoomState.hooked = true;
		if (!changingFov) return; // the hand uses the non-changing call; leave it alone
		float amount = ZoomState.update();
		if (amount <= 0f || !RayConfig.get().zoomEnabled) return;
		double base = MinecraftClient.getInstance().options.getFov().getValue();
		cir.setReturnValue(cir.getReturnValue() * ZoomState.fovFactor(base, amount));
	}
}
