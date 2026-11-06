package dev.ray;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Every piece of text the client draws itself (the Ray menus and the HUD modules) goes through here, so the Client Font
 * setting applies to all of it, and to nothing else.
 *
 * With a client font chosen, the text is given that font explicitly. Because the font is part of the text itself, the
 * Minecraft Font setting (which replaces Minecraft's default font) cannot change it. Without one, or if the font is not
 * loaded, the text is drawn the normal way, so nothing can go missing.
 */
public final class ClientFont {
	private static final int MAX_CACHE = 4096;
	private static final Map<String, Text> CACHE = new HashMap<>();
	private static Identifier cacheFont = null;
	/** Set if Minecraft could not build a styled text once: stop trying and draw normally. */
	private static boolean broken = false;

	private ClientFont() {}

	/** The text with the client font applied, or null to draw it normally. */
	private static Text styled(String s) {
		if (broken || s == null || s.isEmpty()) return null;
		Identifier id = FontOverride.clientFontId();
		if (id == null) return null;
		if (!id.equals(cacheFont) || CACHE.size() > MAX_CACHE) {
			CACHE.clear();
			cacheFont = id;
		}
		Text t = CACHE.get(s);
		if (t != null) return t;
		try {
			JsonObject json = new JsonObject();
			json.addProperty("text", s);
			json.addProperty("font", id.toString());
			t = TextCodecs.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
		} catch (Throwable e) {
			t = null;
		}
		if (t == null) {
			broken = true;
			return null;
		}
		CACHE.put(s, t);
		return t;
	}

	public static void draw(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
		Text t = styled(s);
		if (t != null) ctx.drawText(tr, t, x, y, color, shadow);
		else ctx.drawText(tr, s, x, y, color, shadow);
	}

	public static void draw(DrawContext ctx, TextRenderer tr, Text t, int x, int y, int color, boolean shadow) {
		ctx.drawText(tr, t, x, y, color, shadow);
	}

	public static void draw(DrawContext ctx, TextRenderer tr, OrderedText t, int x, int y, int color, boolean shadow) {
		ctx.drawText(tr, t, x, y, color, shadow);
	}

	public static int width(TextRenderer tr, String s) {
		Text t = styled(s);
		return t != null ? tr.getWidth(t) : tr.getWidth(s);
	}

	public static int width(TextRenderer tr, Text t) {
		return tr.getWidth(t);
	}

	public static int width(TextRenderer tr, OrderedText t) {
		return tr.getWidth(t);
	}
}
