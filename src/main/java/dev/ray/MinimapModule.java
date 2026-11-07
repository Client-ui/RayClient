package dev.ray;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

/**
 * A small north-up map of the terrain around you with your waypoints on it.
 *
 * Terrain is sampled from the loaded chunks (first solid block from just above you downwards) a few times a second
 * and drawn as shaded colours: higher ground is lighter, lower is darker. Waypoints in this dimension show as
 * coloured diamonds; ones outside the map range stick to the edge so you always know which way they are.
 */
public class MinimapModule extends HudModule {
	private static int[] cache = new int[0];
	private static int cacheN = 0;
	private static long lastRefresh = 0L;
	private static final BlockPos.Mutable POS = new BlockPos.Mutable();

	public MinimapModule() {
		super("minimap", "Minimap", "World", false, TR, 6, 6);
	}

	private int cellPx() {
		return opt("size") >= 120 ? 3 : 2;
	}

	// the scan runs a few rows per frame (instead of every row at once) so it never causes a stutter
	private static int scanRow = 0, scanN = 0;
	private static double scanPx, scanPy, scanPz;
	private static boolean scanning = false;

	@Override
	public boolean prepare(MinecraftClient mc, TextRenderer tr, boolean editing) {
		int cp = cellPx();
		int n = Math.max(8, opt("size") / cp);
		w = n * cp;
		h = n * cp;
		if (mc.world != null && mc.player != null) {
			long now = System.currentTimeMillis();
			if (n != cacheN) {
				if (cache.length != n * n) cache = new int[n * n];
				java.util.Arrays.fill(cache, 0xFF0B0D14);
				cacheN = n;
				scanning = false;
				lastRefresh = 0L;
			}
			if (!scanning && now - lastRefresh > 500L) {
				scanning = true;
				scanRow = 0;
				scanN = n;
				scanPx = mc.player.getX();
				scanPy = mc.player.getY();
				scanPz = mc.player.getZ();
			}
			if (scanning) {
				int rowsPerFrame = Math.max(2, n / 8);
				scanRows(mc, n, opt("range"), rowsPerFrame);
			}
		}
		return true;
	}

	private static void scanRows(MinecraftClient mc, int n, int range, int count) {
		if (scanN != n) {
			scanning = false;
			return;
		}
		int startY = (int) Math.floor(scanPy) + 6;
		int endY = (int) Math.floor(scanPy) - 40;
		double perCell = (range * 2.0) / n;
		int end = Math.min(n, scanRow + count);
		for (int j = scanRow; j < end; j++) {
			int bz = (int) Math.floor(scanPz - range + (j + 0.5) * perCell);
			for (int i = 0; i < n; i++) {
				int bx = (int) Math.floor(scanPx - range + (i + 0.5) * perCell);
				int color = 0xFF0B0D14; // nothing loaded / void
				for (int y = startY; y >= endY; y--) {
					POS.set(bx, y, bz);
					BlockState state = mc.world.getBlockState(POS);
					if (state.isAir()) continue;
					color = colorFor(state, y - (int) Math.floor(scanPy));
					break;
				}
				cache[j * n + i] = color;
			}
		}
		scanRow = end;
		if (scanRow >= n) {
			scanning = false;
			lastRefresh = System.currentTimeMillis();
		}
	}

	private static int colorFor(BlockState s, int rel) {
		int base;
		if (!s.getFluidState().isEmpty()) {
			base = s.isOf(Blocks.LAVA) ? 0xE0661A : 0x2F5FBF;
			rel = 0; // water and lava are flat
		} else if (s.isOf(Blocks.GRASS_BLOCK)) base = 0x5DA03C;
		else if (s.getBlock() instanceof LeavesBlock) base = 0x2F7A2A;
		else if (s.isOf(Blocks.SAND)) base = 0xDCCB8A;
		else if (s.isOf(Blocks.RED_SAND)) base = 0xC0652E;
		else if (s.isOf(Blocks.SNOW) || s.isOf(Blocks.SNOW_BLOCK)) base = 0xF0F4F8;
		else if (s.isOf(Blocks.DIRT)) base = 0x86603B;
		else if (s.isOf(Blocks.NETHERRACK)) base = 0x7A2E2E;
		else if (s.isOf(Blocks.END_STONE)) base = 0xD8D8A0;
		else if (s.isOf(Blocks.STONE)) base = 0x8A8A8A;
		else base = 0x707684;
		float shade = Math.max(0.40f, Math.min(1.15f, 0.70f + rel * 0.02f));
		int r = Math.min(255, (int) (((base >> 16) & 0xFF) * shade));
		int g = Math.min(255, (int) (((base >> 8) & 0xFF) * shade));
		int b = Math.min(255, (int) ((base & 0xFF) * shade));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	@Override
	public void draw(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		int cp = cellPx();
		int n = w / cp;
		ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF05070C);

		// terrain, merging runs of equal colour into one rectangle per run
		if (cacheN == n) {
			for (int j = 0; j < n; j++) {
				int i = 0;
				while (i < n) {
					int c = cache[j * n + i];
					int k = i + 1;
					while (k < n && cache[j * n + k] == c) k++;
					ctx.fill(x + i * cp, y + j * cp, x + k * cp, y + (j + 1) * cp, c);
					i = k;
				}
			}
		} else {
			ctx.fill(x, y, x + w, y + h, 0xFF0B0D14);
		}
		if (accentBar()) ctx.fill(x, y, x + w, y + 1, accentColor());

		double px = mc.player.getX(), pz = mc.player.getZ();
		int range = opt("range");
		double half = w / 2.0;

		// waypoints
		if (opt("waypoints") == 1 && mc.world != null) {
			String dim = mc.world.getRegistryKey().getValue().toString();
			RayConfig cfg = RayConfig.get();
			for (int i = 0; i < cfg.waypoints.size(); i++) {
				RayConfig.Waypoint wp = cfg.waypoints.get(i);
				if (!dim.equals(wp.dim)) continue;
				double rx = (wp.x - px) / range * half;
				double rz = (wp.z - pz) / range * half;
				double edge = half - 4.0;
				double m = Math.max(Math.abs(rx), Math.abs(rz));
				boolean outside = m > edge;
				if (outside) {
					rx = rx / m * edge;
					rz = rz / m * edge;
				}
				int col = Theme.COLORS[i % Theme.COLORS.length] | 0xFF000000;
				if (outside) col = Theme.fade(col, 0.7f);
				ctx.getMatrices().pushMatrix();
				ctx.getMatrices().translate((float) (x + half + rx), (float) (y + half + rz));
				ctx.getMatrices().rotate((float) (Math.PI / 4.0));
				ctx.fill(-3, -3, 3, 3, 0xCC000000);
				ctx.fill(-2, -2, 2, 2, col);
				ctx.getMatrices().popMatrix();
			}
		}

		// you: an arrow pointing the way you face (map is north-up, +Z is down)
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate((float) (x + half), (float) (y + half));
		ctx.getMatrices().rotate((float) (Math.toRadians(mc.player.getYaw()) + Math.PI));
		ctx.fill(-3, -5, 4, 2, 0x66000000);
		for (int i = 0; i < 5; i++) ctx.fill(-(i / 2), -4 + i, (i / 2) + 1, -3 + i, 0xFFFFFFFF);
		ctx.getMatrices().popMatrix();

		if (opt("north") == 1) ClientFont.draw(ctx, tr, "N", x + w / 2 - 2, y + 2, 0xFFFFFFFF, true);
	}
}
