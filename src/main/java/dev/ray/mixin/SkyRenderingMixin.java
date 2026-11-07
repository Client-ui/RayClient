package dev.ray.mixin;

import dev.ray.Sky;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Custom Sky, top of the sky. In 1.21.11 the big sky disc is painted by SkyRendering.renderTopSky(int colour) with a
 * colour that comes from the new environment-attribute system (not from ClientWorld any more), so the colour is swapped
 * right where it is handed to the painter. Targeted by name so the project still compiles if Mojang moves the class;
 * if the method is not found the hook is skipped and the vanilla sky stays.
 */
@Mixin(targets = "net.minecraft.client.render.SkyRendering")
public abstract class SkyRenderingMixin {
	@ModifyVariable(method = "renderTopSky", at = @At("HEAD"), argsOnly = true, require = 0)
	private int ray$topSky(int color) {
		MinecraftClient mc = MinecraftClient.getInstance();
		return Sky.active(mc.world) ? Sky.sky(color) : color;
	}
}
