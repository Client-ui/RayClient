package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Draws your saved waypoints in the world: a diamond at the spot with its name and distance. */
public final class WaypointMarkers {
	private WaypointMarkers() {}

	public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
		RayConfig cfg = RayConfig.get();
		if (!cfg.waypointMarkers || cfg.waypoints.isEmpty()) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
		Projection proj = Projection.create(mc, ctx, tickCounter);
		if (proj == null) return;

		TextRenderer tr = mc.textRenderer;
		String dim = mc.world.getRegistryKey().getValue().toString();
		int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
		float[] s = new float[2];

		for (int i = 0; i < cfg.waypoints.size(); i++) {
			RayConfig.Waypoint w = cfg.waypoints.get(i);
			if (!dim.equals(w.dim)) continue;
			double dx = w.x - mc.player.getX(), dy = w.y - mc.player.getY(), dz = w.z - mc.player.getZ();
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (cfg.waypointMaxDist > 0 && dist > cfg.waypointMaxDist) continue;
			if (!proj.project(w.x, w.y + 1.0, w.z, s)) continue;
			if (s[0] < -40 || s[0] > sw + 40 || s[1] < -40 || s[1] > sh + 40) continue;

			int col = Theme.COLORS[i % Theme.COLORS.length] | 0xFF000000;

			// diamond (a square turned 45 degrees) with a dark outline
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(s[0], s[1]);
			ctx.getMatrices().rotate((float) (Math.PI / 4.0));
			ctx.fill(-4, -4, 4, 4, 0xB0000000);
			ctx.fill(-3, -3, 3, 3, col);
			ctx.getMatrices().popMatrix();

			// name above, distance below
			int x = Math.round(s[0]), y = Math.round(s[1]);
			String name = w.name;
			ClientFont.draw(ctx, tr, name, x - ClientFont.width(tr, name) / 2, y - 17, 0xFFFFFFFF, true);
			if (cfg.waypointShowDist) {
				String d = (int) dist + "m";
				ClientFont.draw(ctx, tr, d, x - ClientFont.width(tr, d) / 2, y + 7, 0xFFD0D6E4, true);
			}
		}
	}
}
