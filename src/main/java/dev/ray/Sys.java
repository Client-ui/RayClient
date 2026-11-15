package dev.ray;

import java.nio.file.Files;
import java.nio.file.Path;

/** Tiny OS helpers. */
public final class Sys {
	private Sys() {}

	/**
	 * Opens a folder in the system file browser. Java's own Desktop class does not work inside Minecraft (the game runs
	 * Java in "headless" mode), so this uses Minecraft's opener, and the operating system's own command as a fallback.
	 */
	public static void openFolder(Path p) {
		try {
			Files.createDirectories(p); // opening a folder that does not exist yet would just fail
		} catch (Throwable ignored) {
			// opened below anyway
		}
		try {
			net.minecraft.util.Util.getOperatingSystem().open(p.toUri());
			return;
		} catch (Throwable ignored) {
			// fall through to the system command
		}
		Thread t = new Thread(() -> {
			try {
				String os = System.getProperty("os.name", "").toLowerCase();
				String dir = p.toAbsolutePath().toString();
				if (os.contains("win")) new ProcessBuilder("explorer.exe", dir).start();
				else if (os.contains("mac")) new ProcessBuilder("open", dir).start();
				else new ProcessBuilder("xdg-open", dir).start();
			} catch (Throwable ignored) {
				// nothing more to try: the path is shown on the settings page
			}
		}, "ray-open-folder");
		t.setDaemon(true);
		t.start();
	}
}
