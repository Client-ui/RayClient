package dev.ray.mixin;

import dev.ray.MenuButtons;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-styles Minecraft's plain buttons in the menus outside a world (singleplayer, multiplayer, options...). */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetMixin {
	@Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
	private void ray$styledButton(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
		PressableWidget self = (PressableWidget) (Object) this;
		if (MenuButtons.draw(context, self.getX(), self.getY(), self.getWidth(), self.getHeight(), self.getMessage(),
			self.active, self.isHovered(), self.isFocused(), System.identityHashCode(self))) {
			ci.cancel();
		}
	}
}
