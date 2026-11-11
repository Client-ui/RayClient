package dev.ray.mixin;

import dev.ray.FontOverride;
import net.minecraft.client.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only remembers Minecraft's font manager. It deliberately has no @Shadow fields: the font manager's internals change
 * between Minecraft versions, and a mismatch here used to disable every font feature at once. The two things that
 * do need internals live in their own mixins (FontStoragesMixin, FontOverridesMixin) so each can fail on its own.
 */
@Mixin(FontManager.class)
public class FontManagerMixin {
	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void ray$captured(CallbackInfo ci) {
		FontOverride.manager = this;
	}
}
