package dev.ray.mixin;

import dev.ray.RayConfig;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Low Fire: the burning overlay is drawn at a fixed height of -0.3 (in blocks, relative to your view). Moving that
 * number down lowers the flames so they do not cover the middle of the screen. This only targets that one number, so if
 * it does not exist in this Minecraft version Mixin skips it and the fire simply stays as it is.
 */
@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
	@ModifyConstant(method = "renderFireOverlay", constant = @Constant(floatValue = -0.3F), require = 0)
	private static float ray$lowFire(float original) {
		RayConfig cfg = RayConfig.get();
		if (!cfg.lowFire) return original;
		return original - cfg.lowFireAmount / 100.0f * 0.5f; // 100% = 0.5 blocks lower
	}
}
