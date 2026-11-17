package dev.ray;

import com.google.gson.JsonObject;
import net.minecraft.text.Text;

/** Minecraft 1.20.2 and older: text is read with Text.Serializer.fromJson. */
final class TextJson {
	private TextJson() {}

	static Text parse(JsonObject json) {
		return Text.Serializer.fromJson(json);
	}
}
