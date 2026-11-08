package dev.ray;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/** Save and switch between complete layouts/settings. Waypoints and playtime are kept when switching. */
public final class Profiles {
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("ray-client-profiles");

	private Profiles() {}

	public static List<String> list() {
		List<String> out = new ArrayList<>();
		try {
			if (!Files.isDirectory(DIR)) return out;
			try (Stream<Path> s = Files.list(DIR)) {
				s.forEach(p -> {
					String n = p.getFileName().toString();
					if (n.endsWith(".json")) out.add(n.substring(0, n.length() - 5));
				});
			}
		} catch (IOException ignored) {
		}
		Collections.sort(out);
		return out;
	}

	public static String nextName() {
		List<String> names = list();
		int i = 1;
		while (names.contains("Profile " + i)) i++;
		return "Profile " + i;
	}

	public static boolean save(String name) {
		return RayConfig.saveTo(DIR.resolve(name + ".json"));
	}

	public static boolean load(String name) {
		return RayConfig.loadFrom(DIR.resolve(name + ".json"));
	}

	public static void delete(String name) {
		try {
			Files.deleteIfExists(DIR.resolve(name + ".json"));
		} catch (IOException ignored) {
		}
	}
}
