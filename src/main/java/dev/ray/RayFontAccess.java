package dev.ray;

import net.minecraft.util.Identifier;

/** Implemented by a FontManager mixin so the rest of the client can ask about loaded fonts without touching mixin classes. */
public interface RayFontAccess {
	/** True if a font with this id was loaded from the resources. */
	boolean ray$has(Identifier id);
}
