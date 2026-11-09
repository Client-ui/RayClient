package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Function;

/** A small live bar graph (used for FPS and ping). */
public class GraphModule extends HudModule {
	private final Function<MinecraftClient, Integer> value;
	private final String label;
	private final int floorMax;
	private final int[] hist = new int[40];
	private int idx = 0, current = 0;
	private long last = 0;

	public GraphModule(String id, String name, String category, boolean defaultOn, int anchor, int ox, int oy,
					   String label, int floorMax, Function<MinecraftClient, Integer> value) {
		super(id, name, category, defaultOn, anchor, ox, oy);
		this.label = label;
		this.floorMax = floorMax;
		this.value = value;
	}

	@Override
	public boolean prepare(MinecraftClient mc, TextRenderer tr, boolean editing) {
		long now = System.currentTimeMillis();
		current = value.apply(mc);
		if (now - last >= 250L) {
			last = now;
			hist[idx] = current;
			idx = (idx + 1) % hist.length;
		}
		w = hist.length * 2 + 8;
		h = opt("height");
		return true;
	}

	@Override
	public void draw(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		int accent = accentColor();
		drawBg(ctx, x, y, w, h);
		if (accentBar()) ctx.fill(x, y, x + 2, y + h, accent);
		int max = floorMax;
		for (int v : hist) max = Math.max(max, v);
		int area = Math.max(8, h - 14);
		for (int i = 0; i < hist.length; i++) {
			int v = hist[(idx + i) % hist.length];
			int bar = Math.max(0, Math.min(area, v * area / max));
			int bx = x + 4 + i * 2;
			ctx.fill(bx, y + h - 3 - bar, bx + 2, y + h - 3, Theme.withAlpha(accent, 0xCC));
		}
		drawText(ctx, tr, label + " " + current, x + 5, y + 2, textColor());
	}
}
