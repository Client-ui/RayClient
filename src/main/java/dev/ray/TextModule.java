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

	@Override
	public boolean prepare(MinecraftClient mc, TextRenderer tr, boolean editing) {
		List<String> l = source.apply(mc);
		if (l == null || l.isEmpty()) {
			if (!editing) return false;
			l = List.of(name);
		}
		lines = l;
		int mw = 0;
		for (String s : l) mw = Math.max(mw, ClientFont.width(tr, s));
		w = mw + 8;
		h = l.size() * 12 - 1;
		return true;
	}

	@Override
	public void draw(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		int accent = accentColor();
		int yy = y;
		for (String s : lines) {
			if (hasBg()) ctx.fill(x, yy, x + w, yy + 11, bgColor());
			if (accentBar()) ctx.fill(x, yy, x + 2, yy + 11, accent);
			ClientFont.draw(ctx, tr, s, x + 5, yy + 2, textColor(), shadow());
			yy += 12;
		}
	}
}
