package dev.ray.mixin;

import dev.ray.Brand;
import dev.ray.RayConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the red Mojang Studios loading screen with the dark Ray Client one.
 *
 * Minecraft's own overlay keeps running (it does the real resource loading and the fade-out); the Ray screen is
 * painted over it every frame and fades out on the same schedule. If anything here does not match this Minecraft
 * version, Mixin just skips it and you get the normal loading screen.
 */
@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
	@Shadow
	private long reloadCompleteTime;

	@Inject(method = "render", at = @At("TAIL"), require = 0)
	private void ray$loadingScreen(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!RayConfig.get().customLoading) return;
		long now = Util.getMeasuringTimeMs();
		// -1 while loading, then 0..1 over the one-second fade-out
		float f = reloadCompleteTime > -1L ? (now - reloadCompleteTime) / 1000.0f : -1.0f;
		// stay fully opaque for the first half of the fade so the vanilla logo underneath never shows through
		float alpha = f < 0 ? 1.0f : Math.max(0.0f, Math.min(1.0f, 2.0f * (1.0f - f)));
		if (alpha <= 0.01f) return;
		Brand.drawLoading(context, alpha, now);
	}
}
