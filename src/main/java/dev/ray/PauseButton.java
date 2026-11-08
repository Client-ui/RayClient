package dev.ray;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Adds a "Ray Client" button to the pause (Esc) menu. It opens the HUD editor, which has the Mods and Settings
 * buttons in the middle. The button is placed under the lowest button of the vanilla menu, in the same column.
 */
public final class PauseButton {
	private PauseButton() {}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof GameMenuScreen)) return;
			int bottom = 0, left = scaledWidth / 2 - 102, right = scaledWidth / 2 + 102;
			boolean first = true;
			for (ClickableWidget w : Screens.getButtons(screen)) {
				bottom = Math.max(bottom, w.getY() + w.getHeight());
				if (first || w.getX() < left) left = w.getX();
				if (first || w.getX() + w.getWidth() > right) right = w.getX() + w.getWidth();
				first = false;
			}
			if (bottom <= 0) return;
			int y = bottom + 4;
			if (y + 20 > scaledHeight) y = scaledHeight - 24;
			ButtonWidget button = ButtonWidget.builder(Text.literal("Ray Client  -  HUD & Mods"), b -> client.setScreen(new HudEditorScreen()))
				.dimensions(left, y, right - left, 20)
				.build();
			Screens.getButtons(screen).add(button);
		});
	}
}
