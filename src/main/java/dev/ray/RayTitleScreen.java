package dev.ray;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;

/**
 * Ray Client's own main menu: animated dark backdrop, bolt + wordmark, and re-styled buttons.
 * It replaces the vanilla title screen (see MinecraftClientMixin) and can be switched off in the Client category.
 */
public class RayTitleScreen extends Screen {
	private static final String MC_VERSION = "1.21.11";

	private final Ui ui = new Ui();
	private final long born = System.currentTimeMillis();

	public RayTitleScreen() {
		super(Text.literal("Ray Client"));
	}

	@Override
	protected void init() {
		ui.reset();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private void menuButton(int x, int y, int w, int h, String label, boolean primary, Runnable action) {
		boolean hov = ui.over(x, y, w, h);
		float hv = ui.anim("tb" + label, hov);
		int accent = Theme.accent();
		int fill, stroke;
		if (primary) {
			fill = Theme.mix(0xFF0F1220, accent, 0.38f + 0.17f * hv);
			stroke = Theme.mix(accent, Theme.mix(accent, 0xFFFFFFFF, 0.35f), hv);
		} else {
			fill = Theme.mix(0xFF0F1220, Theme.mix(0xFF12162A, accent, 0.22f), hv);
			stroke = Theme.mix(0xFF2A3047, accent, hv);
		}
		ui.shadow(x, y, w, h, 4);
		ui.panel(x, y, w, h, 4, fill, stroke);
		int line = Math.round((w - 16) * hv);
		if (line > 0) ui.rect(x + (w - line) / 2, y + h - 3, line, 1, Theme.fade(accent, 0.9f)); // underline grows from the centre
		ui.center(label, x + w / 2, y + (h - 8) / 2, Theme.mix(Theme.TEXT, 0xFFFFFFFF, primary ? 1f : hv));
		ui.hit("tb" + label, x, y, w, h, action, null, null);
	}

	/** A soft light that follows the cursor: stacked translucent discs read as a blur. */
	private void cursorGlow(int cx, int cy) {
		if (!RayConfig.get().uiAnimations) return;
		int accent = Theme.accent();
		for (int k = 9; k >= 1; k--) {
			int r = k * 8;
			ui.rrect(cx - r, cy - r, r * 2, r * 2, r, Theme.fade(accent, 0.016f));
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		ui.begin(ctx, textRenderer, mouseX, mouseY);
		Brand.backdrop(ctx, width, height, now, 1.0f);
		cursorGlow(mouseX, mouseY);
		float intro = Math.max(0f, Math.min(1f, (now - born) / 650f));

		int s = Math.max(2, Math.min(4, height / 80));
		int bs = Math.max(2, s - 1);
		int boltH = Brand.boltHeight(bs), wordH = Brand.wordHeight(s);
		int btnW = Math.min(220, width - 40), btnH = 22, gap = 6;
		int total = boltH + 8 + wordH + 8 + 10 + 16 + btnH * 4 + gap * 3;
		int y = Math.max(8, (height - total) / 2 - 2);
		int cx = width / 2;

		Brand.bolt(ctx, cx, y, bs, intro, now);
		y += boltH + 8;
		Brand.wordmark(ctx, cx, y, s, intro);
		y += wordH + 8;
		ui.center("LEGIT CLIENT  -  MINECRAFT " + MC_VERSION, cx, y, Theme.fade(Theme.DIM, intro));
		y += 10 + 16;

		int bx = cx - btnW / 2;
		menuButton(bx, y, btnW, btnH, "SINGLEPLAYER", false, () -> client.setScreen(new SelectWorldScreen(this)));
		y += btnH + gap;
		menuButton(bx, y, btnW, btnH, "MULTIPLAYER", false, () -> client.setScreen(new MultiplayerScreen(this)));
		y += btnH + gap;
		menuButton(bx, y, btnW, btnH, "RAY CLIENT  MODS", true, () -> client.setScreen(new RayScreen(this)));
		y += btnH + gap;
		int half = (btnW - gap) / 2;
		menuButton(bx, y, half, btnH, "OPTIONS", false, () -> client.setScreen(new OptionsScreen(this, client.options)));
		menuButton(bx + half + gap, y, half, btnH, "QUIT", false, () -> client.scheduleStop());

		ui.text("Ray Client v" + RayClient.VERSION, 6, height - 12, Theme.MUTE);
		ui.right("Not affiliated with Mojang AB", width - 6, height - 12, Theme.MUTE);

		ui.poll();
	}
}
