package dev.ray.mixin;

import dev.ray.RayConfig;
import dev.ray.Stretch;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stretched Res. Scales the world projection matrix horizontally / vertically. A copy is returned, so a matrix that
 * the game keeps and re-uses is never scaled twice. If this method does not exist on a Minecraft version the hook
 * simply does not run (the settings page then says so).
 */
@Mixin(GameRenderer.class)
public class GameRendererStretchMixin {
	@SuppressWarnings({"unchecked", "rawtypes"})
	@Inject(method = "getBasicProjectionMatrix", at = @At("RETURN"), cancellable = true, require = 0)
	private void ray$stretch(CallbackInfoReturnable<?> cir) {
		Stretch.hooked = true;
		if (!Stretch.worldCall) return;
		RayConfig c = RayConfig.get();
		if (!c.stretchOn) return;
		Object o = cir.getReturnValue();
		if (!(o instanceof Matrix4f m)) return;
		float sx = c.stretchX / 100.0f, sy = c.stretchY / 100.0f;
		if (sx == 1.0f && sy == 1.0f) return;
		((CallbackInfoReturnable) cir).setReturnValue(new Matrix4f(m).scale(sx, sy, 1.0f));
	}
}
