package dev.ray.mixin;

import dev.ray.Brand;
import dev.ray.RayConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menu screens opened outside a world (singleplayer list, multiplayer list, options, create world...) get the
 * Ray Client backdrop instead of the Minecraft panorama. Ray's own screens already draw it themselves.
 */
@Mixin(Screen.class)
public class ScreenBackgroundMixin {
	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
	private void ray$menuBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		if (MinecraftClient.getInstance().world != null || !RayConfig.get().customBackground) return;
		Screen self = (Screen) (Object) this;
		if (self.getClass().getName().startsWith("dev.ray.")) return;
		Brand.backdrop(context, self.width, self.height, System.currentTimeMillis(), 1.0f);
		ci.cancel();
	}
}
