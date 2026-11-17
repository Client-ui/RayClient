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

/** Same as ScreenBackgroundMixin, for Minecraft 1.20 and 1.20.1 where renderBackground takes only the DrawContext. */
@Mixin(Screen.class)
public class ScreenBackgroundOldMixin {
	@Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void ray$menuBackground(DrawContext context, CallbackInfo ci) {
		if (MinecraftClient.getInstance().world != null || !RayConfig.get().customBackground) return;
		Screen self = (Screen) (Object) this;
		if (self.getClass().getName().startsWith("dev.ray.")) return;
		Brand.backdrop(context, self.width, self.height, System.currentTimeMillis(), 1.0f);
		ci.cancel();
	}
}
