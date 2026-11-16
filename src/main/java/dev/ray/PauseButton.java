package dev.ray;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * The pause (Esc) menu, Ray style: the vanilla buttons are drawn as glass buttons (see {@link MenuButtons}), a Ray Client
 * header sits above them, and a lit "Ray Client - HUD & Mods" button under them opens the HUD editor.
 */
public final class PauseButton {
	private static final Ui UI = new Ui();

	private PauseButton() {}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof GameMenuScreen)) return;
			int top = Integer.MAX_VALUE, bottom = 0, left = scaledWidth / 2 - 102, right = scaledWidth / 2 + 102;
			boolean first = true;
			for (ClickableWidget w : Screens.getButtons(screen)) {
				top = Math.min(top, w.getY());
				bottom = Math.max(bottom, w.getY() + w.getHeight());
				if (first || w.getX() < left) left = w.getX();
				if (first || w.getX() + w.getWidth() > right) right = w.getX() + w.getWidth();
				first = false;
			}
			if (bottom <= 0) return;
			int y = bottom + 6;
			if (y + 24 > scaledHeight) y = scaledHeight - 28;
			ButtonWidget button = ButtonWidget.builder(Text.literal("Ray Client"), b -> client.setScreen(new HudEditorScreen()))
				.dimensions(left, y, right - left, 24)
				.build();
			Screens.getButtons(screen).add(button);

			final int headerTop = top, hl = left, hr = right, heroBottom = y + 24;
			// one card behind the whole menu (drawn before the buttons, so they sit on it)
			ScreenEvents.beforeRender(screen).register((s, ctx, mouseX, mouseY, tickDelta) -> {
				if (!RayConfig.get().styledMenuButtons) return;
				MinecraftClient mc = MinecraftClient.getInstance();
				UI.begin(ctx, mc.textRenderer, -1, -1);
				int pad = 14, headH = 36;
				int cx = hl - pad, cw = (hr - hl) + pad * 2;
				int cy = Math.max(2, headerTop - headH - 8);
				int ch = heroBottom + pad - cy;
				UI.surface(cx, cy, cw, ch, 12);
				if (headerTop - cy >= headH) {
					int lh = 20, hy = cy + 9;
					Logo.draw(ctx, cx + pad, hy, lh, 1f);
					UI.trackedHead("RAY CLIENT", cx + pad + Logo.width(lh) + 8, hy + 6, 0xFFFFFFFF, 2);
					String state = "PAUSED";
					UI.tracked(state, cx + cw - pad - UI.trackedWidth(state, 1, false), hy + 7, Theme.MUTE, 1);
					UI.rect(cx + 1, cy + headH - 2, cw - 2, 1, Theme.LINE);
				}
			});
		});
	}
}
