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
 *
 * Built texts and measured widths are cached, because the menus and the HUD ask for the same strings every frame.
 * The "smooth" variants always use the smooth Outfit font (used by Lyric Sync so lyrics never look pixelated).
 */
public final class ClientFont {
	private static final int MAX_CACHE = 4096;

	/** One cache of built texts and widths for one font. */
	private static final class Face {
		final Map<String, Text> texts = new HashMap<>();
		final Map<String, Integer> widths = new HashMap<>();
		Identifier font = null;

		void check(Identifier id) {
			if (!java.util.Objects.equals(id, font) || texts.size() > MAX_CACHE || widths.size() > MAX_CACHE) {
				texts.clear();
				widths.clear();
				font = id;
			}
		}
	}

	private static final Face CLIENT = new Face(), SMOOTH = new Face();
	/** Set if Minecraft could not build a styled text once: stop trying and draw normally. */
	private static boolean broken = false;

	private ClientFont() {}

	private static Text styled(Face face, Identifier id, String s) {
		if (broken || s == null || s.isEmpty() || id == null) return null;
		Text t = face.texts.get(s);
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
		face.texts.put(s, t);
		return t;
	}

	private static void drawOn(Face face, Identifier id, DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
		face.check(id);
		Text t = styled(face, id, s);
		if (t != null) ctx.drawText(tr, t, x, y, color, shadow);
		else ctx.drawText(tr, s, x, y, color, shadow);
	}

	private static int widthOn(Face face, Identifier id, TextRenderer tr, String s) {
		face.check(id);
		Integer cached = face.widths.get(s);
		if (cached != null) return cached;
		Text t = styled(face, id, s);
		int w = t != null ? tr.getWidth(t) : tr.getWidth(s);
		face.widths.put(s, w);
		return w;
	}

	// ---- the client font (follows the Client Font setting) ----

	public static void draw(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
		drawOn(CLIENT, FontOverride.clientFontId(), ctx, tr, s, x, y, color, shadow);
	}

	public static int width(TextRenderer tr, String s) {
		return widthOn(CLIENT, FontOverride.clientFontId(), tr, s);
	}

	// ---- always the smooth font (Lyric Sync) ----

	public static void drawSmooth(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
		drawOn(SMOOTH, FontOverride.smoothId(), ctx, tr, s, x, y, color, shadow);
	}

	public static int widthSmooth(TextRenderer tr, String s) {
		return widthOn(SMOOTH, FontOverride.smoothId(), tr, s);
	}

	// ---- already-built texts pass straight through ----

	public static void draw(DrawContext ctx, TextRenderer tr, Text t, int x, int y, int color, boolean shadow) {
		ctx.drawText(tr, t, x, y, color, shadow);
	}

	public static void draw(DrawContext ctx, TextRenderer tr, OrderedText t, int x, int y, int color, boolean shadow) {
		ctx.drawText(tr, t, x, y, color, shadow);
	}

	public static int width(TextRenderer tr, Text t) {
		return tr.getWidth(t);
	}

	public static int width(TextRenderer tr, OrderedText t) {
		return tr.getWidth(t);
	}
}
