package dev.ray.mixin;

import dev.ray.FontOverride;
import dev.ray.RayFontAccess;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.FontStorage;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Gives the client access to Minecraft's font manager: the id override map (default font -> Ray's font) and the list
 * of fonts that were actually loaded. If any of this does not match the running Minecraft version Mixin skips it and
 * fonts simply stay at the default.
 */
@Mixin(FontManager.class)
public class FontManagerMixin implements RayFontAccess {
	@Shadow
	private Map<Identifier, Identifier> idOverrides;

	@Shadow
	@Final
	private Map<Identifier, FontStorage> fontStorages;

	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void ray$captured(CallbackInfo ci) {
		FontOverride.manager = this;
	}

	@Override
	public void ray$setOverrides(Map<Identifier, Identifier> overrides) {
		this.idOverrides = overrides;
	}

	@Override
	public boolean ray$has(Identifier id) {
		return fontStorages.containsKey(id);
	}
}
