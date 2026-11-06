package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lyric Sync display.
 *
 *  1. Top bar: the line being sung, with a karaoke sweep that fills word by word, a sliding transition between lines
 *     and a little equaliser.
 *  2. Floating words: every word pops into the world at the moment it is sung, placed where your crosshair points,
 *     and then stays in place in the world. Words pile up (sentence on a row, rows stacking downward) and the whole
 *     set fades after a few seconds so a fresh one can start.
 */
public final class LyricsHud {
	private static final Ui UI = new Ui();

	private static final class Word {
		double x, y, z;          // centre in the world
		double rx, rz;           // unit vector pointing to the reader's right (horizontal)
		String text;
		int w;                   // width in font pixels
		long born, die;
	}

	private static final List<Word> WORDS = new ArrayList<>();
	private static List<Lrc.Line> lastLines = null;
	private static int lineIdx = -2;
	private static int spawned = 0;
	private static String[] words = new String[0];
	private static float[] starts = new float[0];
	private static int[] prefix = new int[0], ww = new int[0];
	private static int lineW = 0;
	private static long lineWall = 0L;
	private static String shownText = "", prevText = "";
	private static float lineFrac = 0f;
	private static long groupEnd = 0L;

	// sentence layout: where the current group is anchored (centre of the first row) and how many rows are used
	private static double ax, ay, az, arx, arz;
	private static int rows = 0;
	// per-word crosshair layout: the row being built
	private static Word lastWord = null;
	private static double rowLx, rowLy, rowLz, rowRx, rowRz, rowExtent;

	private static float barAlpha = 0f, barW = 0f;
	private static long lastNanos = 0L;

	private static final String[] NOTE = {
		"...#####",
		"...#...#",
		"...#...#",
		"...#...#",
		"...#...#",
		"..##..##",
		".###.###",
		".###.###",
		"..#...#."
	};

	private LyricsHud() {}

	/** Pixels at the top of the screen the lyrics bar is using (Toasts move below it). */
	public static int occupiedHeight() {
		RayConfig cfg = RayConfig.get();
		if (!cfg.lyricsOn || !cfg.lyricsBar || barAlpha < 0.05f) return 0;
		return cfg.lyricsY + (cfg.lyricsShowTitle ? 40 : 28);
	}

	// ---------- entry point ----------

	public static void render(DrawContext ctx, RenderTickCounter tc) {
		RayConfig cfg = RayConfig.get();
		if (!cfg.lyricsOn) {
			if (!WORDS.isEmpty()) clearWorld();
			barAlpha = 0f;
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

		long nowN = System.nanoTime();
		float dt = lastNanos == 0L ? 0.016f : Math.min(0.1f, (nowN - lastNanos) / 1.0e9f);
		lastNanos = nowN;

		long pos = Lyrics.positionMs();
		List<Lrc.Line> lines = Lyrics.lines;
		int idx = (Lyrics.status == Lyrics.Status.READY && !lines.isEmpty()) ? Lrc.indexAt(lines, pos) : -1;
		update(mc, tc, cfg, idx, pos, lines);

		UI.begin(ctx, mc.textRenderer, -1, -1);
		if (cfg.lyricsWorld) drawWords(ctx, mc, tc, cfg);
		if (cfg.lyricsBar) drawBar(ctx, mc, cfg, dt);
	}

	private static void clearWorld() {
		WORDS.clear();
		lastWord = null;
		groupEnd = 0L;
		rows = 0;
	}

	// ---------- following the song ----------

	private static void update(MinecraftClient mc, RenderTickCounter tc, RayConfig cfg, int idx, long pos, List<Lrc.Line> lines) {
		long now = System.currentTimeMillis();
		if (lines != lastLines) {
			lastLines = lines;
			lineIdx = -2;
			clearWorld();
			shownText = "";
			prevText = "";
		}
		if (lineIdx >= 0 && idx < lineIdx) { // the song was rewound or restarted
			clearWorld();
			lineIdx = -2;
		}
		if (idx != lineIdx) {
			prevText = shownText;
			lineIdx = idx;
			spawned = 0;
			lineWall = now;
			shownText = idx >= 0 ? lines.get(idx).text() : "";
			prepareLine(mc.textRenderer, shownText);
		}
		if (idx < 0 || words.length == 0) {
			lineFrac = 0f;
			return;
		}

		long lineStart = lines.get(idx).timeMs();
		float span = Lrc.durationOf(lines, idx) * 0.92f;
		lineFrac = Math.max(0f, Math.min(1f, (pos - lineStart) / span));

		if (!cfg.lyricsWorld || !Lyrics.playing) return;
		// joined a line late (new song, seek): do not dump a pile of old words at once
		while (spawned < words.length && pos - (lineStart + starts[spawned] * span) > 1500L) spawned++;
		while (spawned < words.length && pos >= lineStart + starts[spawned] * span) {
			spawnWord(mc, tc, cfg, spawned, now);
			spawned++;
		}
	}

	private static void prepareLine(TextRenderer tr, String text) {
		if (text.isEmpty()) {
			words = new String[0];
			starts = new float[0];
			prefix = new int[0];
			ww = new int[0];
			lineW = 0;
			return;
		}
		words = text.split(" ");
		starts = Lrc.wordStarts(words);
		prefix = new int[words.length];
		ww = new int[words.length];
		int space = Math.max(2, ClientFont.width(tr, " "));
		int acc = 0;
		for (int i = 0; i < words.length; i++) {
			ww[i] = ClientFont.width(tr, words[i]);
			prefix[i] = acc;
			acc += ww[i] + space;
		}
		lineW = Math.max(1, acc - space);
	}

	// ---------- placing words in the world ----------

	/** World size of one font pixel, in blocks. */
	private static double bpp(RayConfig cfg) {
		return 0.045 * cfg.lyricsSize / 100.0;
	}

	private static boolean groupAlive(long now) {
		return !WORDS.isEmpty() && now < groupEnd;
	}

	private static void spawnWord(MinecraftClient mc, RenderTickCounter tc, RayConfig cfg, int k, long now) {
		float tp = tc.getTickProgress(false);
		Vec3d cam = mc.player.getCameraPosVec(tp);
		double yaw = Math.toRadians(FreeLook.active && FreeLook.hooked ? FreeLook.yaw : mc.player.getYaw(tp));
		double pit = Math.toRadians(FreeLook.active && FreeLook.hooked ? FreeLook.pitch : mc.player.getPitch(tp));
		double fx = -Math.sin(yaw) * Math.cos(pit), fy = -Math.sin(pit), fz = Math.cos(yaw) * Math.cos(pit);
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw);

		// how far ahead: the chosen distance, but never inside a wall you are looking at
		double dist = cfg.lyricsDistance;
		HitResult hr = mc.crosshairTarget;
		if (hr != null && hr.getType() != HitResult.Type.MISS) {
			dist = Math.min(dist, Math.max(1.5, hr.getPos().distanceTo(cam) - 0.4));
		}
		double cx = cam.x + fx * dist, cy = cam.y + fy * dist, cz = cam.z + fz * dist;

		double bpp = bpp(cfg);
		double lineH = 13.0 * bpp;
		Word w = new Word();
		w.text = words[k];
		w.w = ww[k];
		w.born = now;

		boolean fresh = !groupAlive(now);
		if (fresh) {
			groupEnd = now + cfg.lyricsClear * 1000L;
			lastWord = null;
			rows = 0;
			ax = cx;
			ay = cy;
			az = cz;
			arx = rx;
			arz = rz;
		}
		w.die = groupEnd;

		if (cfg.lyricsMode == 0) {
			// Sentence: the whole line is laid out around one anchor; the next line stacks below it
			if (!fresh && k == 0) rows++;
			double off = (prefix[k] + ww[k] / 2.0 - lineW / 2.0) * bpp;
			w.x = ax + arx * off;
			w.z = az + arz * off;
			w.y = ay - rows * lineH;
			w.rx = arx;
			w.rz = arz;
		} else {
			// Per-word crosshair: the word lands where you are looking; if that spot is taken by the previous word
			// it continues the sentence to the right, and when the row is full it starts a new row below.
			double wBlocks = ww[k] * bpp;
			double gap = 4.0 * bpp;
			w.x = cx;
			w.y = cy;
			w.z = cz;
			w.rx = rx;
			w.rz = rz;
			Word p = lastWord;
			boolean newRow = true;
			if (p != null) {
				double dx = cx - p.x, dz = cz - p.z;
				double along = dx * p.rx + dz * p.rz;
				double across = Math.abs(-dx * p.rz + dz * p.rx);
				double pw = p.w * bpp;
				boolean sameSpot = Math.abs(along) < (pw + wBlocks) / 2.0 + gap && Math.abs(cy - p.y) < lineH * 0.9 && across < 1.2;
				if (sameSpot) {
					newRow = false;
					double maxRow = 230.0 * bpp;
					if (rowExtent + gap + wBlocks > maxRow) {
						// row full: back to the row start, one row lower
						rowLy -= lineH;
						w.x = rowLx + rowRx * wBlocks / 2.0;
						w.z = rowLz + rowRz * wBlocks / 2.0;
						w.y = rowLy;
						w.rx = rowRx;
						w.rz = rowRz;
						rowExtent = wBlocks;
					} else {
						w.x = rowLx + rowRx * (rowExtent + gap + wBlocks / 2.0);
						w.z = rowLz + rowRz * (rowExtent + gap + wBlocks / 2.0);
						w.y = rowLy;
						w.rx = rowRx;
						w.rz = rowRz;
						rowExtent += gap + wBlocks;
					}
				}
			}
			if (newRow) {
				rowRx = rx;
				rowRz = rz;
				rowLx = cx - rx * wBlocks / 2.0;
				rowLz = cz - rz * wBlocks / 2.0;
				rowLy = cy;
				rowExtent = wBlocks;
			}
		}
		WORDS.add(w);
		lastWord = w;
		while (WORDS.size() > 90) WORDS.remove(0);
	}

	// ---------- drawing the floating words ----------

	private static void drawWords(DrawContext ctx, MinecraftClient mc, RenderTickCounter tc, RayConfig cfg) {
		if (WORDS.isEmpty()) return;
		long now = System.currentTimeMillis();
		Iterator<Word> it = WORDS.iterator();
		while (it.hasNext()) if (now > it.next().die + 500L) it.remove();
		if (WORDS.isEmpty()) return;
		if (mc.currentScreen != null) return;
		Projection proj = Projection.create(mc, ctx, tc);
		if (proj == null) return;

		float tp = tc.getTickProgress(false);
		double yaw = Math.toRadians(FreeLook.active && FreeLook.hooked ? FreeLook.yaw : mc.player.getYaw(tp));
		double crx = -Math.cos(yaw), crz = -Math.sin(yaw); // camera right, used to measure how big a block looks
		double bpp = bpp(cfg);
		int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
		TextRenderer tr = mc.textRenderer;
		int base = Theme.pick(cfg.lyricsColor, Theme.accent());
		float[] a0 = new float[2], a1 = new float[2];

		for (Word w : WORDS) {
			long age = now - w.born;
			float in = Math.max(0f, Math.min(1f, age / 260f));
			float out = now > w.die ? Math.max(0f, 1f - (now - w.die) / 500f) : 1f;
			float alpha = Math.min(Math.min(1f, in * 1.6f), out);
			if (alpha <= 0.01f) continue;
			if (!proj.project(w.x, w.y, w.z, a0)) continue;
			if (!proj.project(w.x + crx * 0.5, w.y, w.z + crz * 0.5, a1)) continue;
			float pxPerBlock = Math.abs(a1[0] - a0[0]) / 0.5f;
			float pop = 0.55f + 0.45f * Theme.easeOutBack(in);
			float scale = (float) (pxPerBlock * bpp) * pop * (0.92f + 0.08f * out);
			if (scale < 0.2f || scale > 12f) continue;
			if (a0[0] < -80 || a0[0] > sw + 80 || a0[1] < -40 || a0[1] > sh + 40) continue;

			// freshly sung words flash white, then settle into the chosen colour
			float flash = Math.max(0f, 1f - age / 650f);
			int col = Theme.mix(base, 0xFFFFFFFF, flash * 0.85f);
			int x0 = -w.w / 2, y0 = -4;
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(a0[0], a0[1]);
			ctx.getMatrices().scale(scale, scale);
			int glow = Theme.fade(base, 0.22f * alpha);
			ClientFont.draw(ctx, tr, w.text, x0 - 1, y0, glow, false);
			ClientFont.draw(ctx, tr, w.text, x0 + 1, y0, glow, false);
			ClientFont.draw(ctx, tr, w.text, x0, y0 - 1, glow, false);
			ClientFont.draw(ctx, tr, w.text, x0, y0 + 1, glow, false);
			ClientFont.draw(ctx, tr, w.text, x0 + 1, y0 + 1, Theme.fade(0xFF000000, 0.5f * alpha), false);
			ClientFont.draw(ctx, tr, w.text, x0, y0, Theme.fade(col, alpha), false);
			ctx.getMatrices().popMatrix();
		}
	}

	// ---------- drawing the top bar ----------

	private static void drawBar(DrawContext ctx, MinecraftClient mc, RayConfig cfg, float dt) {
		long now = System.currentTimeMillis();
		Lyrics.Status st = Lyrics.status;
		boolean have = Lyrics.spotifyFound && !Lyrics.title.isEmpty() && st != Lyrics.Status.IDLE;
		if (st == Lyrics.Status.MISSING && now - Lyrics.trackSince > 6000L) have = false;
		float target = !have ? 0f : (Lyrics.playing ? 1f : 0.55f);
		barAlpha += (target - barAlpha) * (1f - (float) Math.exp(-dt * 9f));
		if (barAlpha < 0.01f) return;
		float A = barAlpha;

		TextRenderer tr = mc.textRenderer;
		int sw = ctx.getScaledWindowWidth();
		int accent = Theme.accent();

		String text;
		boolean dots = false;
		if (st == Lyrics.Status.LOADING) text = "Finding lyrics...";
		else if (st == Lyrics.Status.MISSING) text = "No lyrics for this song";
		else {
			text = shownText;
			dots = text.isEmpty();
		}

		int avail = Math.max(120, Math.round(sw * 0.7f)) - 62;
		int tw = ClientFont.width(tr, text);
		float s = tw > avail ? Math.max(0.6f, avail / (float) tw) : 1f;
		int textPx = Math.round(tw * s);
		int innerW = Math.max(64, Math.min(textPx, avail));
		int targetW = 6 + 16 + 9 + innerW + 9 + 14 + 8;
		if (barW <= 1f) barW = targetW;
		barW += (targetW - barW) * (1f - (float) Math.exp(-dt * 12f));
		int bw = Math.round(barW), bh = 22;
		int bx = (sw - bw) / 2, by = cfg.lyricsY;

		UI.shadow(bx, by, bw, bh, 11);
		UI.glow(bx, by, bw, bh, 11, accent, 0.55f * A);
		UI.panelGrad(bx, by, bw, bh, 11, Theme.fade(Theme.PANEL_TOP, 0.94f * A), Theme.fade(Theme.PANEL, 0.94f * A), Theme.fade(accent, 0.65f * A));

		// music tile
		UI.rrectGrad(bx + 4, by + 3, 16, 16, 8, Theme.fade(Theme.mix(accent, 0xFFFFFFFF, 0.25f), A), Theme.fade(accent, A));
		UI.pix(NOTE, bx + 8, by + 6, 1, Theme.fade(0xFFFFFFFF, A));

		// text area (clipped so lines can slide in and out)
		int areaX = bx + 6 + 16 + 9;
		int areaW = bw - (6 + 16 + 9) - (9 + 14 + 8);
		if (areaW > 8) {
			ctx.enableScissor(areaX, by, areaX + areaW, by + bh);
			float t = Math.max(0f, Math.min(1f, (now - lineWall) / 340f));
			float e = Theme.easeOutCubic(t);
			if (!cfg.uiAnimations) e = 1f;

			// the line that just ended slides up and fades
			if (e < 0.98f && st == Lyrics.Status.READY && !prevText.isEmpty()) {
				drawLine(ctx, tr, prevText, areaX, areaW, by + 7 - Math.round(e * 8f), 1f, Theme.fade(Theme.DIM, (1f - e) * 0.8f * A), -1f);
			}
			if (dots) {
				for (int i = 0; i < 3; i++) {
					float pulse = 0.5f + 0.5f * (float) Math.sin(now / 260.0 - i * 0.9);
					UI.circle(areaX + areaW / 2 - 8 + i * 8, by + bh / 2, 2, Theme.fade(accent, (0.35f + 0.65f * pulse) * A * e));
				}
			} else {
				float sungFrac = st == Lyrics.Status.READY ? lineFrac : -1f;
				drawLine(ctx, tr, text, areaX, areaW, by + 7 + Math.round((1f - e) * 8f), s, Theme.fade(Theme.TEXT, 0.5f * A * e), sungFrac, accent, A * e);
			}
			ctx.disableScissor();
		}

		// equaliser
		int ex = bx + bw - 8 - 14, base = by + bh - 6;
		for (int i = 0; i < 4; i++) {
			int h = Lyrics.playing ? 3 + Math.round(8f * Math.abs((float) Math.sin(now / 210.0 + i * 1.9))) : 3;
			UI.rrect(ex + i * 4, base - h + 1, 2, h, 1, Theme.fade(Theme.mix(accent, 0xFFFFFFFF, 0.2f), 0.9f * A));
		}

		// song name under the bar
		if (cfg.lyricsShowTitle && !Lyrics.title.isEmpty()) {
			String info = Lyrics.artist.isEmpty() ? Lyrics.title : Lyrics.artist + " - " + Lyrics.title;
			info = UI.fit(info, Math.max(80, sw / 2));
			float is = 0.8f;
			int iw = Math.round(ClientFont.width(tr, info) * is);
			UI.scaledText(info, (sw - iw) / 2, by + bh + 5, is, Theme.fade(Theme.DIM, 0.85f * A), true);
		}
	}

	private static void drawLine(DrawContext ctx, TextRenderer tr, String text, int areaX, int areaW, int y, float s, int dim, float sungFrac) {
		drawLine(ctx, tr, text, areaX, areaW, y, s, dim, sungFrac, 0, 0f);
	}

	/** Draws a line centred in the text area. With sungFrac >= 0 the part already sung is painted bright (karaoke). */
	private static void drawLine(DrawContext ctx, TextRenderer tr, String text, int areaX, int areaW, int y, float s, int dim, float sungFrac, int accent, float litAlpha) {
		int tw = Math.round(ClientFont.width(tr, text) * s);
		int tx = areaX + (areaW - tw) / 2;
		if (tw > areaW) tx = areaX; // too long even when shrunk: start at the left edge
		drawScaled(ctx, tr, text, tx, y, s, dim);
		if (sungFrac < 0f || words.length == 0) return;

		// find the word being sung and how far through it we are
		int k = 0;
		for (int i = 0; i < starts.length; i++) if (starts[i] <= sungFrac) k = i;
		float next = k + 1 < starts.length ? starts[k + 1] : 1f;
		float inWord = Math.max(0f, Math.min(1f, (sungFrac - starts[k]) / Math.max(0.001f, next - starts[k])));
		float sungPx = sungFrac >= 1f ? lineW : prefix[k] + inWord * ww[k];
		int clipW = Math.round(sungPx * s);
		if (clipW <= 0) return;
		ctx.enableScissor(Math.max(areaX, tx), y - 3, Math.min(areaX + areaW, tx + clipW), y + 14);
		drawScaled(ctx, tr, text, tx, y, s, Theme.fade(0xFFFFFFFF, litAlpha));
		// soft accent underline that grows with the sweep
		UI.rect(tx, y + 11, clipW, 1, Theme.fade(accent, 0.7f * litAlpha));
		ctx.disableScissor();
	}

	private static void drawScaled(DrawContext ctx, TextRenderer tr, String text, int x, int y, float s, int color) {
		if (Math.abs(s - 1f) < 0.01f) {
			ClientFont.draw(ctx, tr, text, x, y, color, false);
			return;
		}
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate((float) x, (float) y);
		ctx.getMatrices().scale(s, s);
		ClientFont.draw(ctx, tr, text, 0, 0, color, false);
		ctx.getMatrices().popMatrix();
	}
}
