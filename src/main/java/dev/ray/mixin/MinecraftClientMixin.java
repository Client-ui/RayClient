package dev.ray.mixin;

import dev.ray.RayConfig;
import dev.ray.RayTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Swaps the vanilla main menu for the Ray Client one whenever Minecraft opens it. */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
	private Screen ray$brandedTitle(Screen screen) {
		if (screen != null && screen.getClass() == TitleScreen.class && RayConfig.get().customTitle) {
			return new RayTitleScreen();
		}
		return screen;
	}
}
