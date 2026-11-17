package dev.ray;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The pure rules of the updater (version order, which file to download, what to trust). No Minecraft classes, so it can be tested alone. */
public final class UpdateLogic {
	private static final Pattern SHA = Pattern.compile("(?i)sha-?256\\s*[:=]\\s*([0-9a-f]{64})");

	private UpdateLogic() {}

	/** Turns "v1.10.0" or "1.10.0-beta" into its numbers: {1, 10, 0}. */
	static int[] parts(String v) {
		if (v == null) return new int[]{0};
		String s = v.trim();
		if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
		int cut = s.indexOf('-');
		if (cut >= 0) s = s.substring(0, cut);
		cut = s.indexOf('+');
		if (cut >= 0) s = s.substring(0, cut);
		String[] bits = s.split("\\.");
		int[] out = new int[bits.length];
		for (int i = 0; i < bits.length; i++) {
			try {
				out[i] = Integer.parseInt(bits[i].replaceAll("[^0-9]", ""));
			} catch (NumberFormatException e) {
				out[i] = 0;
			}
		}
		return out;
	}

	/** Positive when a is newer than b. A release (1.10.0) counts as newer than its own pre-release (1.10.0-beta). */
	public static int compare(String a, String b) {
		int[] x = parts(a), y = parts(b);
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
			if (p != q) return Integer.compare(p, q);
		}
		boolean preA = a != null && a.contains("-"), preB = b != null && b.contains("-");
		if (preA != preB) return preA ? -1 : 1;
		return 0;
	}

	/** True for the jar files that can be the mod itself (not sources, dev or javadoc jars). */
	private static boolean isModJar(String name) {
		String n = name.toLowerCase();
		return n.endsWith(".jar") && !n.contains("sources") && !n.contains("-dev") && !n.contains("javadoc");
	}

	/**
	 * Index of the file to download from a release's file names: the mod jar built for this Minecraft version
	 * (named like ray-client-mc1.21.4-1.1.1.jar), never the sources jar. A release carries one jar per Minecraft version, so
	 * if jars named for other versions exist but none for this one, this returns -1 rather than a jar that would break the game.
	 * Releases whose jars carry no version in their name (the old single-jar layout) still work. -1 if there is none.
	 */
	public static int pickAsset(List<String> names, String mcVersion) {
		int best = -1;
		boolean anyVersioned = false;
		String marker = "mc" + (mcVersion == null ? "" : mcVersion.toLowerCase()) + "-";
		for (int i = 0; i < names.size(); i++) {
			String n = names.get(i).toLowerCase();
			if (!isModJar(n)) continue;
			if (n.matches(".*mc\\d+\\.\\d+.*")) anyVersioned = true;
			if (mcVersion != null && n.contains(marker)) return i;
		}
		if (anyVersioned) return -1;
		for (int i = 0; i < names.size(); i++) {
			String n = names.get(i).toLowerCase();
			if (!isModJar(n)) continue;
			if (best < 0 || (n.startsWith("ray-client") && !names.get(best).toLowerCase().startsWith("ray-client"))) best = i;
		}
		return best;
	}

	/**
	 * The SHA-256 the release notes promise for one file ("name.jar  sha256: ..." on one line), or for a single-jar release the
	 * first "sha256: ..." in the notes, or an empty string.
	 */
	public static String shaFromNotes(String notes, String assetName) {
		if (notes == null) return "";
		if (assetName != null && !assetName.isEmpty()) {
			for (String line : notes.split("\\R")) {
				if (!line.contains(assetName)) continue;
				Matcher m = SHA.matcher(line);
				if (m.find()) return m.group(1).toLowerCase();
			}
			if (notes.toLowerCase().contains(".jar  sha256")) return ""; // per-file hashes exist but none for this file
		}
		Matcher m = SHA.matcher(notes);
		return m.find() ? m.group(1).toLowerCase() : "";
	}

	/** Downloads are only ever taken from GitHub over HTTPS. */
	public static boolean trustedUrl(String url) {
		try {
			URI u = URI.create(url);
			String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
			return "https".equals(u.getScheme()) && (host.equals("github.com") || host.endsWith(".github.com") || host.endsWith(".githubusercontent.com"));
		} catch (Exception e) {
			return false;
		}
	}
}
