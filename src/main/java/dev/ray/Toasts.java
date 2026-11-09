package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small notifications that slide in at the top of the screen ("FPS: ON", "Profile loaded", "Death point saved").
 * Each one has a status dot (green for on / saved, red for off / errors, accent otherwise), glides down when it appears,
 * makes room smoothly when a newer one arrives, and shows a thin countdown line while it waits to leave.
 */
public final class Toasts {
	private record Toast(String text, long born) {}

	private static final long LIFE = 3000L, FADE_IN = 320L, FADE_OUT = 420L;
	private static final int MAX = 4;
	private static final List<Toast> LIST = new ArrayList<>();
	private static final Map<Long, Float> SHOWN_Y = new HashMap<>();
	private static final Ui UI = new Ui();

	private Toasts() {}

	public static void push(String text) {
		if (!RayConfig.get().toastsOn || text == null || text.isEmpty()) return;
		if (!LIST.isEmpty() && LIST.get(LIST.size() - 1).text().equals(text)) LIST.remove(LIST.size() - 1); // repeated: replace
		LIST.add(new Toast(text, System.currentTimeMillis()));
		while (LIST.size() > MAX) LIST.remove(0);
	}

	private static int tone(String text, int accent) {
		String l = text.toLowerCase();
		if (l.endsWith(" on") || l.endsWith(": on")) return Theme.GOOD;
		if (l.endsWith(" off") || l.endsWith(": off")) return Theme.BAD;
		if (l.contains("error") || l.contains("failed") || l.contains("problem") || l.contains("paused")) return Theme.BAD;
		if (l.contains("saved") || l.contains("loaded") || l.contains("ready") || l.contains("downloaded") || l.contains("updated")) return Theme.GOOD;
		return accent;
	}

	public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
		if (LIST.isEmpty()) return;
		long now = System.currentTimeMillis();
		LIST.removeIf(t -> now - t.born() > LIFE);
		SHOWN_Y.keySet().removeIf(k -> LIST.stream().noneMatch(t -> t.born() == k));
		if (LIST.isEmpty()) return;

		MinecraftClient mc = MinecraftClient.getInstance();
		UI.begin(ctx, mc.textRenderer, -1, -1);
		int sw = ctx.getScaledWindowWidth();
		int accent = Theme.accent();
		int y = Math.max(12, LyricsHud.occupiedHeight());
		for (int i = LIST.size() - 1; i >= 0; i--) { // newest on top
			Toast t = LIST.get(i);
			long age = now - t.born();
			float in = Math.min(1f, age / (float) FADE_IN);
			float out = age > LIFE - FADE_OUT ? Math.max(0f, (LIFE - age) / (float) FADE_OUT) : 1f;
			float a = Math.min(Theme.easeOutCubic(in), out);
			int tw = UI.w(t.text());
			int bw = tw + 38, bh = 22;
			int bx = (sw - bw) / 2;

			// the stack glides to its place instead of jumping
			Float cur = SHOWN_Y.get(t.born());
			float ty = cur == null ? y : cur + (y - cur) * (1f - (float) Math.exp(-UI.dt * 14f));
			SHOWN_Y.put(t.born(), ty);
			int by = Math.round(ty) - Math.round((1f - Theme.easeOutBack(in)) * 16f) - (out < 1f ? Math.round((1f - out) * 6f) : 0);

			int tone = tone(t.text(), accent);
			UI.shadow(bx, by, bw, bh, 8);
			UI.panelGrad(bx, by, bw, bh, 8, Theme.fade(0xFF171A20, 0.97f * a), Theme.fade(0xFF101217, 0.97f * a), Theme.fade(Theme.STROKE_HI, a));
			// status dot with a soft halo
			UI.circle(bx + 13, by + bh / 2, 5, Theme.fade(tone, 0.20f * a));
			UI.circle(bx + 13, by + bh / 2, 2, Theme.fade(tone, a));
			UI.text(t.text(), bx + 24, by + 7, Theme.fade(Theme.TEXT, a));
			// countdown line
			float life = Math.max(0f, 1f - age / (float) LIFE);
			int lw = Math.max(0, Math.round((bw - 24) * life));
			if (lw > 1) UI.rrect(bx + 12, by + bh - 3, lw, 1, 0, Theme.fade(tone, 0.55f * a));
			y += bh + 5;
		}
	}
}
