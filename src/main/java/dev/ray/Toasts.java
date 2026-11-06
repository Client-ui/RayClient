package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.ArrayList;
import java.util.List;

/** Small notifications that slide in at the top of the screen ("FPS: ON", "Profile loaded", "Death point saved"). */
public final class Toasts {
	private record Toast(String text, long born) {}

	private static final long LIFE = 2600L, FADE_IN = 200L, FADE_OUT = 380L;
	private static final int MAX = 4;
	private static final List<Toast> LIST = new ArrayList<>();
	private static final Ui UI = new Ui();

	private Toasts() {}

	public static void push(String text) {
		if (!RayConfig.get().toastsOn || text == null || text.isEmpty()) return;
		if (!LIST.isEmpty() && LIST.get(LIST.size() - 1).text().equals(text)) LIST.remove(LIST.size() - 1); // repeated: replace
		LIST.add(new Toast(text, System.currentTimeMillis()));
		while (LIST.size() > MAX) LIST.remove(0);
	}

	public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
		if (LIST.isEmpty()) return;
		long now = System.currentTimeMillis();
		LIST.removeIf(t -> now - t.born() > LIFE);
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
			float a = Math.min(in, out);
			float ease = 1f - (1f - in) * (1f - in); // slides down into place
			int tw = UI.w(t.text());
			int bw = tw + 38, bh = 20;
			int bx = (sw - bw) / 2;
			int by = y - Math.round((1f - ease) * 14f);
			float life = Math.max(0f, 1f - age / (float) LIFE);
			UI.shadow(bx, by, bw, bh, 10);
			UI.glow(bx, by, bw, bh, 10, accent, 0.5f * a);
			UI.panelGrad(bx, by, bw, bh, 10, Theme.fade(Theme.PANEL_TOP, 0.96f * a), Theme.fade(Theme.PANEL, 0.96f * a), Theme.fade(accent, 0.6f * a));
			UI.circle(bx + 11, by + bh / 2, 3, Theme.fade(accent, a));
			UI.text(t.text(), bx + 20, by + 6, Theme.fade(Theme.TEXT, a));
			UI.rrect(bx + 10, by + bh - 3, Math.max(2, Math.round((bw - 20) * life)), 1, 0, Theme.fade(accent, 0.55f * a));
			y += bh + 5;
		}
	}
}
