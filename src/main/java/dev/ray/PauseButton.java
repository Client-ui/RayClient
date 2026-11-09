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

			final int headerTop = top, hl = left, hr = right;
			ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, tickDelta) -> {
				if (!RayConfig.get().styledMenuButtons) return;
				MinecraftClient mc = MinecraftClient.getInstance();
				UI.begin(ctx, mc.textRenderer, -1, -1);
				int accent = Theme.accent();
				int w = hr - hl, h = 30, hy = headerTop - h - 8;
				if (hy < 4) return;
				UI.surface(hl, hy, w, h, 8);
				Brand.bolt(ctx, hl + 16, hy + 4, 1, 1.0f, System.currentTimeMillis());
				UI.spaced("RAY CLIENT", hl + 34, hy + 6, 0xFFFFFFFF, 2);
				UI.text("Paused  -  v" + RayClient.VERSION, hl + 34, hy + 17, Theme.DIM);
				String hint = "Right Shift: HUD editor";
				UI.right(hint, hr - 10, hy + 11, Theme.MUTE);
			});
		});
	}
}
