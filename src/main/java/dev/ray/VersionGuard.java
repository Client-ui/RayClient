package dev.ray;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Ray Client is built for exactly one Minecraft version. Minecraft's code changes in every release, so the same jar
 * cannot work on another one. Fabric already refuses to load the mod on a different version (see fabric.mod.json);
 * this is a second guard inside the client with a clear explanation, in case that check is bypassed.
 */
public final class VersionGuard {
	public static final String SUPPORTED = "1.21.11";

	private VersionGuard() {}

	public static String detected() {
		return FabricLoader.getInstance().getModContainer("minecraft")
			.map(c -> c.getMetadata().getVersion().getFriendlyString())
			.orElse("unknown");
	}

	public static boolean supported() {
		return isSupported(detected());
	}

	static boolean isSupported(String v) {
		return v.equals(SUPPORTED) || v.startsWith(SUPPORTED + ".");
	}

	/** Throws with a clear message if this is not the Minecraft version the client was built for. */
	public static void requireSupported() {
		String v = detected();
		if (!isSupported(v)) throw new UnsupportedVersionException(problem(v));
	}

	/** A plain-language explanation of why this Minecraft version cannot run this build. */
	public static String problem(String v) {
		String head = "Ray Client " + RayClient.VERSION + " only supports Minecraft " + SUPPORTED + ", but this is Minecraft " + v + ". ";
		int major = leading(v, 0), minor = leading(v, 1);
		if (v.startsWith("1.") && minor >= 0 && minor < 14) {
			return head + "Fabric (the mod system Ray Client runs on) does not exist for Minecraft versions before 1.14, so Ray Client cannot run on this version.";
		}
		if (major >= 26) {
			return head + "Minecraft " + v + " belongs to the newer unobfuscated 26.x line, which needs its own Ray Client build. Use Minecraft " + SUPPORTED + " or a Ray Client build made for " + v + ".";
		}
		return head + "Minecraft changes its internals in every release, so each version needs its own Ray Client build. Use Minecraft " + SUPPORTED + " or a Ray Client build made for " + v + ".";
	}

	private static int leading(String v, int part) {
		String[] p = v.split("[.\\-+ ]");
		if (part >= p.length) return -1;
		try {
			return Integer.parseInt(p[part]);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** Thrown at startup on an unsupported Minecraft version; shows up in the crash report. */
	public static final class UnsupportedVersionException extends RuntimeException {
		public UnsupportedVersionException(String message) {
			super(message);
		}
	}
}
