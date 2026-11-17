package dev.ray;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

/** Minecraft 1.20.3 and newer: text is read through its codec. */
final class TextJson {
	private TextJson() {}

	static Text parse(JsonObject json) {
		return TextCodecs.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
	}
}
