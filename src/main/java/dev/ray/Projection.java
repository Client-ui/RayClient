package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Projects world positions onto the screen using the interpolated first-person camera (same maths as BlockOutline). */
public final class Projection {
	private static final double NEAR = 0.05;

	private final double cx, cy, cz;
	private final double fx, fy, fz, rx, rz, ux, uy, uz;
	private final double xs, ys, hx, hy;

	private Projection(double cx, double cy, double cz, double yaw, double pitch, double fovDeg, int sw, int sh) {
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		double cyaw = Math.cos(yaw), syaw = Math.sin(yaw), cpit = Math.cos(pitch), spit = Math.sin(pitch);
		fx = -syaw * cpit;
		fy = -spit;
		fz = cyaw * cpit;
		rx = -cyaw;
		rz = -syaw;
		ux = -syaw * spit;
		uy = cpit;
		uz = cyaw * spit;
		double tanHalf = Math.tan(Math.toRadians(fovDeg) / 2.0);
		xs = sw / 2.0 / (tanHalf * ((double) sw / sh));
		ys = sh / 2.0 / tanHalf;
		hx = sw / 2.0;
		hy = sh / 2.0;
	}

	/** Returns null when there is no first-person camera to project from. */
	public static Projection create(MinecraftClient mc, DrawContext ctx, RenderTickCounter tickCounter) {
		if (mc.player == null || mc.world == null) return null;
		if (!mc.options.getPerspective().isFirstPerson()) return null;
		RayConfig cfg = RayConfig.get();
		float tp = tickCounter.getTickProgress(false);
		var cam = mc.player.getCameraPosVec(tp);
		double baseFov = mc.options.getFov().getValue();
		double fov = baseFov * (ZoomState.hooked && cfg.zoomEnabled ? ZoomState.fovFactor(baseFov, ZoomState.update()) : 1.0);
		return new Projection(cam.x, cam.y, cam.z,
			Math.toRadians(FreeLook.active && FreeLook.hooked ? FreeLook.yaw : mc.player.getYaw(tp)),
			Math.toRadians(FreeLook.active && FreeLook.hooked ? FreeLook.pitch : mc.player.getPitch(tp)), fov,
			ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
	}

	/** Projects the segment a-b (world coordinates), clipped at the camera near plane. out = {x1, y1, x2, y2}. False if not visible. */
	public boolean projectSegment(double ax, double ay, double az, double bx, double by, double bz, float[] out) {
		double[] a = toCamera(ax, ay, az), b = toCamera(bx, by, bz);
		if (a[2] < NEAR && b[2] < NEAR) return false;
		if (a[2] < NEAR) a = clipToNear(a, b);
		else if (b[2] < NEAR) b = clipToNear(b, a);
		out[0] = (float) (hx + a[0] / a[2] * xs);
		out[1] = (float) (hy - a[1] / a[2] * ys);
		out[2] = (float) (hx + b[0] / b[2] * xs);
		out[3] = (float) (hy - b[1] / b[2] * ys);
		return true;
	}

	private double[] toCamera(double wx, double wy, double wz) {
		double dx = wx - cx, dy = wy - cy, dz = wz - cz;
		return new double[]{dx * rx + dz * rz, dx * ux + dy * uy + dz * uz, dx * fx + dy * fy + dz * fz};
	}

	private static double[] clipToNear(double[] behind, double[] front) {
		double t = (NEAR - behind[2]) / (front[2] - behind[2]);
		return new double[]{behind[0] + (front[0] - behind[0]) * t, behind[1] + (front[1] - behind[1]) * t, NEAR};
	}

	/** Screen position of a world point (scaled GUI pixels) written to out[0], out[1]. False if it is behind the camera. */
	public boolean project(double wx, double wy, double wz, float[] out) {
		double dx = wx - cx, dy = wy - cy, dz = wz - cz;
		double z = dx * fx + dy * fy + dz * fz;
		if (z < NEAR) return false;
		double x = dx * rx + dz * rz;
		double y = dx * ux + dy * uy + dz * uz;
		out[0] = (float) (hx + x / z * xs);
		out[1] = (float) (hy - y / z * ys);
		return true;
	}
}
