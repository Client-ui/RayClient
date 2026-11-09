package dev.ray;

import net.minecraft.client.gui.screen.Screen;

/** Settings page for a standalone feature (zoom, crosshair, block outline...). */
public class FeatureSettingsScreen extends SettingsScreen {
	private final Features.Rows rows;

	public FeatureSettingsScreen(Screen parent, String name, Features.Rows rows) {
		super(parent, name + " Settings");
		this.rows = rows;
		FreeLook.listening = false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !FreeLook.listening; // while waiting for a key press, ESC cancels the prompt instead of closing
	}

	@Override
	public void close() {
		FreeLook.listening = false;
		super.close();
	}

	@Override
	protected void rows(Ui ui, Ui.Form f) {
		rows.build(this, ui, f);
	}
}
