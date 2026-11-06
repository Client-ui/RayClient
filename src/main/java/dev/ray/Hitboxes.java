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
 * colour while your crosshair is on them. Vanilla's red eye-height line and blue look-direction line are optional.
 *
 * Fair-play rules built in: only entities you can actually see are drawn (an eye-to-eye line-of-sight check, so
 * nothing shows through walls) and invisible entities are skipped.
 */
public final class Hitboxes {
	private static final int[][] EDGES = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
	private static final double MAX_DIST_SQ = 64.0 * 64.0;

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
		float[] seg = new float[4];

		for (Entity e : mc.world.getEntities()) {
			if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive() || e.isInvisible()) continue;
			if (cfg.hitboxTarget == 0 && !(e instanceof PlayerEntity)) continue;
			if (mc.player.squaredDistanceTo(e) > MAX_DIST_SQ) continue;
			if (!mc.player.canSee(e)) continue; // never through walls

			Vec3d p = e.getLerpedPos(tp);
			double hw = e.getWidth() / 2.0, ht = e.getHeight();
			double[][] c = {
				{p.x - hw, p.y, p.z - hw}, {p.x + hw, p.y, p.z - hw}, {p.x + hw, p.y, p.z + hw}, {p.x - hw, p.y, p.z + hw},
				{p.x - hw, p.y + ht, p.z - hw}, {p.x + hw, p.y + ht, p.z - hw}, {p.x + hw, p.y + ht, p.z + hw}, {p.x - hw, p.y + ht, p.z + hw}
			};
			int rgb = (cfg.hitboxHover && e == hovered) ? cfg.hitboxHoverColor : cfg.hitboxColor;
			int color = (alpha << 24) | (rgb & 0xFFFFFF);
			for (int[] edge : EDGES) {
				double[] a = c[edge[0]], b = c[edge[1]];
				if (proj.projectSegment(a[0], a[1], a[2], b[0], b[1], b[2], seg)) {
					ScreenLines.line(ctx, seg[0], seg[1], seg[2], seg[3], thick, gs, color);
				}
			}

			double eyeY = p.y + (e.getEyeY() - e.getY());
			if (cfg.hitboxEye) { // vanilla's red line at eye height
				int red = (alpha << 24) | 0xFF2020;
				double[][] ring = {{p.x - hw, eyeY, p.z - hw}, {p.x + hw, eyeY, p.z - hw}, {p.x + hw, eyeY, p.z + hw}, {p.x - hw, eyeY, p.z + hw}};
				for (int i = 0; i < 4; i++) {
					double[] a = ring[i], b = ring[(i + 1) % 4];
					if (proj.projectSegment(a[0], a[1], a[2], b[0], b[1], b[2], seg)) {
						ScreenLines.line(ctx, seg[0], seg[1], seg[2], seg[3], thick, gs, red);
					}
				}
			}
			if (cfg.hitboxLook) { // vanilla's blue line along where they look
				Vec3d look = e.getRotationVec(tp);
				if (proj.projectSegment(p.x, eyeY, p.z, p.x + look.x * 2.0, eyeY + look.y * 2.0, p.z + look.z * 2.0, seg)) {
					ScreenLines.line(ctx, seg[0], seg[1], seg[2], seg[3], thick, gs, (alpha << 24) | 0x4060FF);
				}
			}
		}
	}
}
