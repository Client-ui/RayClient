package dev.ray.mixin;

import dev.ray.RayFontOverrides;
import net.minecraft.client.font.FontManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

/** Lets the client swap which font replaces Minecraft's default (the same mechanism "Force Unicode Font" uses). */
@Mixin(FontManager.class)
public class FontOverridesMixin implements RayFontOverrides {
	@Shadow
	private Map<Identifier, Identifier> idOverrides;

	@Override
	public void ray$setOverrides(Map<Identifier, Identifier> overrides) {
		this.idOverrides = overrides;
	}
}
