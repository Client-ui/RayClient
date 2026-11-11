package dev.ray.mixin;

import dev.ray.RayFontAccess;
import net.minecraft.client.font.FontManager;
import net.minecraft.client.font.FontStorage;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

/** Lets the client ask whether a font was loaded (used for the custom font only; the bundled fonts are always there). */
@Mixin(FontManager.class)
public class FontStoragesMixin implements RayFontAccess {
	@Shadow
	@Final
	private Map<Identifier, FontStorage> fontStorages;

	@Override
	public boolean ray$has(Identifier id) {
		Map<?, ?> m = fontStorages;
		if (m.containsKey(id)) return true;
		// newer versions key fonts by a wrapper object instead of the bare id; its text form still contains the id
		String s = id.toString();
		for (Object k : m.keySet()) {
			if (k != null && k.toString().contains(s)) return true;
		}
		return false;
	}
}
