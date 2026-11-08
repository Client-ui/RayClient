package dev.ray;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Custom Sky: 26 pre-made skies (plus your own colours). The module recolours three things the game already draws:
 * the sky above you, the horizon / distance fog it fades into, and the clouds. Nothing is added to the world, so it
 * costs no performance and works on any server (it is purely how your own screen looks).
 */
public final class Sky {
	public record Preset(String name, int top, int horizon, int cloud) {}

	public static final Preset[] PRESETS = {
		new Preset("Sunset", 0x5B3A8C, 0xFF8A4C, 0xFFC38A),
		new Preset("Golden Hour", 0x7FB2E5, 0xFFD27A, 0xFFE9B8),
		new Preset("Midnight", 0x050816, 0x0E1530, 0x1A2140),
		new Preset("Aurora", 0x06231F, 0x1DBF8A, 0x6FE3C1),
		new Preset("Nebula", 0x1B0B3A, 0x7B2FBE, 0xB07CFF),
		new Preset("Cotton Candy", 0xF7A8D8, 0xA8E6FF, 0xFFFFFF),
		new Preset("Blood Moon", 0x260606, 0xB3121B, 0x6B1A1A),
		new Preset("Deep Ocean", 0x0A4F7A, 0x38C6D9, 0xCFF5FF),
		new Preset("Overcast", 0x7B8794, 0xB0B8C2, 0xD7DCE2),
		new Preset("Synthwave", 0x120030, 0xFF2E93, 0x00E5FF),
		new Preset("Sakura", 0xE8A0C0, 0xFFD6E0, 0xFFFFFF),
		new Preset("Emerald", 0x04281C, 0x2ECC71, 0xA6F5C8),
		new Preset("Volcano", 0x1A0500, 0xFF4A12, 0xFF9A55),
		new Preset("Arctic", 0x1B4F72, 0xBDE9FF, 0xFFFFFF),
		new Preset("Twilight", 0x241B54, 0xE96A8D, 0xF2A6C2),
		new Preset("Desert Noon", 0x3FA9F5, 0xF6E3B0, 0xFFF5DC),
		new Preset("Storm", 0x2B3038, 0x5B6573, 0x8A94A0),
		new Preset("Neon City", 0x07031A, 0x7C3AED, 0xF0ABFC),
		new Preset("Rose Gold", 0x6B3A4A, 0xF4B6A6, 0xFFE0D6),
		new Preset("Toxic", 0x0F1F05, 0x9BE000, 0xD7FF6A),
		new Preset("Deep Space", 0x000000, 0x1A1033, 0x3B2A6B),
		new Preset("Peach Fizz", 0xFF9A8B, 0xFFE3B3, 0xFFFFFF),
		new Preset("Lagoon", 0x00606B, 0x5EEAD4, 0xE0FFF8),
		new Preset("Crimson Dawn", 0x2B0A2B, 0xFF5E5B, 0xFFB199),
		new Preset("Lavender", 0x7C6BC4, 0xE6D5FF, 0xFFFFFF),
		new Preset("Monochrome", 0x111111, 0x666666, 0xBBBBBB),
	};
	public static final int CUSTOM = PRESETS.length;

	// how bright the vanilla value is in full daylight; used by "Follow Day Cycle"
	private static final float REF_SKY = 0.55f, REF_FOG = 0.80f, REF_CLOUD = 0.90f;

	private Sky() {}

	public static String presetName(int idx) {
		return idx >= 0 && idx < PRESETS.length ? PRESETS[idx].name() : "Custom";
	}

	public static int top(int idx) {
		RayConfig c = RayConfig.get();
		return idx >= 0 && idx < PRESETS.length ? PRESETS[idx].top() : c.skyCustomTop & 0xFFFFFF;
	}

	public static int horizon(int idx) {
		RayConfig c = RayConfig.get();
		return idx >= 0 && idx < PRESETS.length ? PRESETS[idx].horizon() : c.skyCustomHorizon & 0xFFFFFF;
	}

	public static int cloud(int idx) {
		RayConfig c = RayConfig.get();
		return idx >= 0 && idx < PRESETS.length ? PRESETS[idx].cloud() : c.skyCustomCloud & 0xFFFFFF;
	}

	/** Only the overworld is recoloured: the Nether and End have their own atmosphere. */
	public static boolean active(ClientWorld world) {
		return RayConfig.get().skyOn && world != null && world.getRegistryKey() == World.OVERWORLD;
	}

	private static float lum(int rgb) {
		return (0.299f * ((rgb >> 16) & 0xFF) + 0.587f * ((rgb >> 8) & 0xFF) + 0.114f * (rgb & 0xFF)) / 255f;
	}

	private static int scale(int rgb, float f) {
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * f));
		int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * f));
		int b = Math.min(255, Math.round((rgb & 0xFF) * f));
		return (r << 16) | (g << 8) | b;
	}

	private static int blend(int vanilla, int target, float ref) {
		RayConfig c = RayConfig.get();
		if (c.skyFollowDay) target = scale(target, Math.max(0.12f, Math.min(1f, lum(vanilla) / ref)));
		return Theme.mix(0xFF000000 | vanilla, 0xFF000000 | target, c.skyStrength / 100.0f) & 0xFFFFFF;
	}

	// ---- hooks: each takes the vanilla value and returns the recoloured one ----

	public static int sky(int vanillaArgb) {
		return (vanillaArgb & 0xFF000000) | blend(vanillaArgb & 0xFFFFFF, top(RayConfig.get().skyPreset), REF_SKY);
	}

	public static int fog(int vanillaArgb) {
		return (vanillaArgb & 0xFF000000) | blend(vanillaArgb & 0xFFFFFF, horizon(RayConfig.get().skyPreset), REF_FOG);
	}

	public static int clouds(int vanillaArgb) {
		return (vanillaArgb & 0xFF000000) | blend(vanillaArgb & 0xFFFFFF, cloud(RayConfig.get().skyPreset), REF_CLOUD);
	}

	public static Vec3d sky(Vec3d v) {
		return fromRgb(blend(toRgb(v), top(RayConfig.get().skyPreset), REF_SKY));
	}

	public static Vec3d fog(Vec3d v) {
		return fromRgb(blend(toRgb(v), horizon(RayConfig.get().skyPreset), REF_FOG));
	}

	public static Vec3d clouds(Vec3d v) {
		return fromRgb(blend(toRgb(v), cloud(RayConfig.get().skyPreset), REF_CLOUD));
	}

	public static float[] fog(float r, float g, float b) {
		int out = blend(toRgb(r, g, b), horizon(RayConfig.get().skyPreset), REF_FOG);
		return new float[]{((out >> 16) & 0xFF) / 255f, ((out >> 8) & 0xFF) / 255f, (out & 0xFF) / 255f};
	}

	private static int toRgb(Vec3d v) {
		return toRgb((float) v.x, (float) v.y, (float) v.z);
	}

	private static int toRgb(float r, float g, float b) {
		int ri = Math.max(0, Math.min(255, Math.round(r * 255f)));
		int gi = Math.max(0, Math.min(255, Math.round(g * 255f)));
		int bi = Math.max(0, Math.min(255, Math.round(b * 255f)));
		return (ri << 16) | (gi << 8) | bi;
	}

	private static Vec3d fromRgb(int rgb) {
		return new Vec3d(((rgb >> 16) & 0xFF) / 255.0, ((rgb >> 8) & 0xFF) / 255.0, (rgb & 0xFF) / 255.0);
	}
}
