package dev.ray;

import java.nio.file.Path;

/** Tiny OS helpers. */
public final class Sys {
	private Sys() {}

	/** Opens a folder in the system file browser on a background thread (best effort, silent on failure). */
	public static void openFolder(Path p) {
		Thread t = new Thread(() -> {
			try {
				if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(p.toFile());
			} catch (Throwable ignored) {
				// nothing to do: the path is shown on screen as a fallback
			}
		}, "ray-open-folder");
		t.setDaemon(true);
		t.start();
	}
}
