package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Custom hitboxes: draws a box around other players (or living entities) in your own colour, with a different
 * colour while your crosshair is on them. Vanilla's red eye-height line and the look-direction line are optional.
 *
 * Fair-play rules built in: only entities you can actually see are drawn (an eye-to-eye line-of-sight check, so
 * nothing shows through walls) and invisible entities are skipped.
 */
public final class Hitboxes {
	private static final int[][] EDGES = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
	private static final double MAX_DIST_SQ = 64.0 * 64.0;

	private static final java.util.List<Entity> VISIBLE = new java.util.ArrayList<>();
	private static final double[] CORNERS = new double[24];
	private static final float[] SEG = new float[4];
	private static long lastScan = 0L;
	private static Object lastWorld = null;
	private static int lastTarget = -1;

	private Hitboxes() {}

	public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
		RayConfig cfg = RayConfig.get();
		if (!cfg.hitbox) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
		Projection proj = Projection.create(mc, ctx, tickCounter);
		if (proj == null) return;

		Entity hovered = mc.crosshairTarget instanceof EntityHitResult ehr ? ehr.getEntity() : null;
		float tp = tickCounter.getTickProgress(false);
		float gs = (float) mc.getWindow().getScaleFactor();
		int thick = Math.max(1, Math.round(cfg.hitboxThickness * gs / 2.0f));
		int alpha = Math.max(20, Math.min(100, cfg.hitboxOpacity)) * 255 / 100;

		// Which entities qualify (alive, in range, and actually visible: the line-of-sight raycast is the expensive part)
		// is worked out about 15 times a second; the boxes themselves are still placed every frame so they move smoothly.
		long now = System.currentTimeMillis();
		if (now - lastScan > 66L || lastWorld != mc.world || lastTarget != cfg.hitboxTarget) {
			lastScan = now;
			lastWorld = mc.world;
			lastTarget = cfg.hitboxTarget;
			VISIBLE.clear();
			for (Entity e : mc.world.getEntities()) {
				if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive() || e.isInvisible()) continue;
				if (cfg.hitboxTarget == 0 && !(e instanceof PlayerEntity)) continue;
				if (mc.player.squaredDistanceTo(e) > MAX_DIST_SQ) continue;
				if (!mc.player.canSee(e)) continue; // never through walls
				VISIBLE.add(e);
			}
		}

		for (int n = 0, size = VISIBLE.size(); n < size; n++) {
			Entity e = VISIBLE.get(n);
			if (!e.isAlive() || e.isInvisible()) continue;
			Vec3d p = e.getLerpedPos(tp);
			double hw = e.getWidth() / 2.0, ht = e.getHeight();
			double x0 = p.x - hw, x1 = p.x + hw, y0 = p.y, y1 = p.y + ht, z0 = p.z - hw, z1 = p.z + hw;
			double[] c = CORNERS;
			c[0] = x0; c[1] = y0; c[2] = z0;
			c[3] = x1; c[4] = y0; c[5] = z0;
			c[6] = x1; c[7] = y0; c[8] = z1;
			c[9] = x0; c[10] = y0; c[11] = z1;
			c[12] = x0; c[13] = y1; c[14] = z0;
			c[15] = x1; c[16] = y1; c[17] = z0;
			c[18] = x1; c[19] = y1; c[20] = z1;
			c[21] = x0; c[22] = y1; c[23] = z1;
			int rgb = (cfg.hitboxHover && e == hovered) ? cfg.hitboxHoverColor : cfg.hitboxColor;
			int color = (alpha << 24) | (rgb & 0xFFFFFF);
			for (int[] edge : EDGES) {
				int ia = edge[0] * 3, ib = edge[1] * 3;
				if (proj.projectSegment(c[ia], c[ia + 1], c[ia + 2], c[ib], c[ib + 1], c[ib + 2], SEG)) {
					ScreenLines.line(ctx, SEG[0], SEG[1], SEG[2], SEG[3], thick, gs, color);
				}
			}

			double eyeY = p.y + (e.getEyeY() - e.getY());
			if (cfg.hitboxEye) { // vanilla's red line at eye height
				int red = (alpha << 24) | 0xFF2020;
				for (int i = 0; i < 4; i++) {
					double ax = (i == 0 || i == 3) ? x0 : x1, az = (i < 2) ? z0 : z1;
					int j = (i + 1) % 4;
					double bx = (j == 0 || j == 3) ? x0 : x1, bz = (j < 2) ? z0 : z1;
					if (proj.projectSegment(ax, eyeY, az, bx, eyeY, bz, SEG)) {
						ScreenLines.line(ctx, SEG[0], SEG[1], SEG[2], SEG[3], thick, gs, red);
					}
				}
			}
			if (cfg.hitboxLook) { // the line along where they look (in the client accent)
				Vec3d look = e.getRotationVec(tp);
				if (proj.projectSegment(p.x, eyeY, p.z, p.x + look.x * 2.0, eyeY + look.y * 2.0, p.z + look.z * 2.0, SEG)) {
					ScreenLines.line(ctx, SEG[0], SEG[1], SEG[2], SEG[3], thick, gs, (alpha << 24) | (Theme.accent() & 0xFFFFFF));
				}
			}
		}
	}
}
