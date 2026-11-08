package dev.ray;

import net.minecraft.util.Identifier;

import java.util.Map;

/** Implemented by the FontManager mixin so the rest of the client can swap fonts without touching mixin classes. */
public interface RayFontAccess {
	/** Replaces the font id overrides (the same mechanism Minecraft's "Force Unicode Font" option uses). */
	void ray$setOverrides(Map<Identifier, Identifier> overrides);

	/** True if a font with this id was loaded from the resources. */
	boolean ray$has(Identifier id);
}
