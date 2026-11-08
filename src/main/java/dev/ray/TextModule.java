package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.function.Function;

public class TextModule extends HudModule {
	private final Function<MinecraftClient, List<String>> source;
	private List<String> lines = List.of();

	public TextModule(String id, String name, String category, boolean defaultOn, int anchor, int ox, int oy,
					  Function<MinecraftClient, List<String>> source) {
		super(id, name, category, defaultOn, anchor, ox, oy);
		this.source = source;
	}

	private long lastUpdate = 0L;
	private boolean lastVisible = true;
	private List<String> lastSource = null;

	/** The text is re-read about ten times a second (plenty for numbers you read), and its width only when it changed. */
	@Override
	public boolean prepare(MinecraftClient mc, TextRenderer tr, boolean editing) {
		long now = System.currentTimeMillis();
		if (editing || now - lastUpdate >= 100L || lastSource == null) {
			lastUpdate = now;
			List<String> l = source.apply(mc);
			boolean visible = l != null && !l.isEmpty();
			if (!visible) {
				lastVisible = false;
				if (!editing) return false;
				l = List.of(name);
			} else {
				lastVisible = true;
			}
			if (!l.equals(lastSource)) {
				lastSource = l;
				lines = l;
				int mw = 0;
				for (String s : l) mw = Math.max(mw, ClientFont.width(tr, s));
				w = mw + 8;
				h = l.size() * 12 - 1;
			}
			return true;
		}
		return lastVisible;
	}

	/** First line of the live value, for the preview on the menu tiles (null if there is nothing to show). */
	@Override
	public String preview(MinecraftClient mc) {
		try {
			List<String> l = source.apply(mc);
			return l == null || l.isEmpty() ? null : l.get(0);
		} catch (Throwable t) {
			return null;
		}
	}

	@Override
	public void draw(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		int accent = accentColor();
		boolean bg = hasBg(), bar = accentBar(), shadow = shadow();
		int bgCol = bgColor(), text = textColor();
		int yy = y;
		for (int i = 0, n = lines.size(); i < n; i++) {
			if (bg) ctx.fill(x, yy, x + w, yy + 11, bgCol);
			if (bar) ctx.fill(x, yy, x + 2, yy + 11, accent);
			ClientFont.draw(ctx, tr, lines.get(i), x + 5, yy + 2, text, shadow);
			yy += 12;
		}
	}
}
