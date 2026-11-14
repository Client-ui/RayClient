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

	/** Built texts and measured widths for one font id. */
	private static final class Sub {
		final Map<String, Text> texts = new HashMap<>();
		final Map<String, Integer> widths = new HashMap<>();
	}

	/** The caches of one kind of text: one {@link Sub} per font id, because the id changes with the on-screen size. */
	private static final class Face {
		final Map<Identifier, Sub> subs = new HashMap<>();

		Sub sub(Identifier id) {
			Sub s = subs.get(id);
			if (s == null) {
				s = new Sub();
				subs.put(id, s);
			} else if (s.texts.size() > MAX_CACHE || s.widths.size() > MAX_CACHE) {
				s.texts.clear();
				s.widths.clear();
			}
			return s;
		}
	}

	/**
	 * How many GUI units one unit of the current drawing area is. Menus that scale themselves to fit the window set this to
	 * their scale while they draw (and Ui resets it to 1 afterwards), so the font can be picked to match the real size.
	 */
	public static float scale = 1f;

	private static final Face CLIENT = new Face(), SMOOTH = new Face(), HEAD = new Face();
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ray");
	/** Which JSON shape of the "font" field Minecraft accepted (0 = plain id, 1 and 2 = object forms), or -1 if none has yet. */
	private static int form = -1;
	/** Set if no shape worked: draw normally for a while, then try again (so one bad moment is never permanent). */
	private static long brokenUntil = 0L;
	private static boolean reported = false;

	private ClientFont() {}

	static Text parse(String s, Identifier id, int shape) {
		try {
			JsonObject json = new JsonObject();
			json.addProperty("text", s);
			if (shape == 0) {
				json.addProperty("font", id.toString());
			} else {
				// newer Minecraft versions describe the font as an object
				JsonObject f = new JsonObject();
				f.addProperty("type", shape == 1 ? "minecraft:font" : "font");
				f.addProperty("id", id.toString());
				json.add("font", f);
			}
			return TextCodecs.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null);
		} catch (Throwable e) {
			return null;
		}
	}

	private static Text styled(Sub face, Identifier id, String s) {
		if (s == null || s.isEmpty() || id == null) return null;
		Text t = face.texts.get(s);
		if (t != null) return t;
		long now = System.currentTimeMillis();
		if (now < brokenUntil) return null;
		if (form >= 0) t = parse(s, id, form);
		if (t == null) {
			for (int shape = 0; shape < 3 && t == null; shape++) {
				t = parse(s, id, shape);
				if (t != null) {
					form = shape;
					if (!reported) LOG.info("[Ray] client font text built with the font form {} (font {})", shape, id);
					reported = true;
				}
			}
		}
		if (t == null) {
			brokenUntil = now + 15000L;
			LOG.warn("[Ray] could not build styled text for font {}; drawing with Minecraft's font for now", id);
			return null;
		}
		face.texts.put(s, t);
		return t;
	}

	private static void drawOn(Face faces, Identifier id, DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
		Text t = styled(faces.sub(id), id, s);
		if (t == null) {
			ctx.drawText(tr, s, x, y, color, shadow);
			return;
		}
		if (FontOverride.isPixel(id)) {
			// Pixel-exact text: the font is a bitmap drawn so that one texture pixel is exactly one screen pixel. For that
			// the text is drawn in screen-pixel units (the scale is divided out) from an origin on the pixel grid, which
			// is what makes every stem the same width and every letter evenly spaced.
			float real = Math.max(0.5f, FontOverride.real());
			int dy = Math.round(7.6f * real) - 7; // Minecraft puts the baseline 7 units down; we want it 7.6 units down
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate((float) x, (float) y);
			ctx.getMatrices().scale(1f / real, 1f / real);
			ctx.drawText(tr, t, 0, dy, color, shadow);
			ctx.getMatrices().popMatrix();
			return;
		}
		ctx.drawText(tr, t, x, y, color, shadow);
	}

	private static int widthOn(Face faces, Identifier id, TextRenderer tr, String s) {
		Sub sub = faces.sub(id);
		Integer cached = sub.widths.get(s);
		if (cached != null) return cached;
		Text t = styled(sub, id, s);
		int w;
		if (t != null && FontOverride.isPixel(id)) {
			w = (int) Math.ceil(tr.getWidth(t) / Math.max(0.5f, FontOverride.real())); // screen pixels back to layout units
		} else {
			w = t != null ? tr.getWidth(t) : tr.getWidth(s);
		}
		sub.widths.put(s, w);
		return w;
	}

	// ---- the client font (follows the Client Font setting) ----

	public static void draw(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
		drawOn(CLIENT, FontOverride.clientFontId(), ctx, tr, s, x, y, color, shadow);
	}

	public static int width(TextRenderer tr, String s) {
		return widthOn(CLIENT, FontOverride.clientFontId(), tr, s);
	}

	/** Text with a 1px dark outline on four sides (the outline keeps the alpha of the text colour). */
	public static void drawOutlined(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
		int oc = (int) ((((long) (color >>> 24)) * 0xD0 / 0xFF) << 24);
		draw(ctx, tr, s, x - 1, y, oc, false);
		draw(ctx, tr, s, x + 1, y, oc, false);
		draw(ctx, tr, s, x, y - 1, oc, false);
		draw(ctx, tr, s, x, y + 1, oc, false);
		draw(ctx, tr, s, x, y, color, false);
	}

	// ---- the bold heading face ----

	public static void drawHead(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color, boolean shadow) {
		Identifier id = FontOverride.headId();
		if (id == null) {
			draw(ctx, tr, s, x, y, color, shadow);
			return;
		}
		drawOn(HEAD, id, ctx, tr, s, x, y, color, shadow);
	}

	public static int widthHead(TextRenderer tr, String s) {
		Identifier id = FontOverride.headId();
		return id == null ? width(tr, s) : widthOn(HEAD, id, tr, s);
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
