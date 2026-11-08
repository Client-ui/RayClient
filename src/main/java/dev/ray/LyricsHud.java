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
 *  1. The bar: the line being sung with a karaoke sweep, a progress line, the next line as a preview, the song name and
 *     time, and a play / pause tile with an equaliser. It comes as a glass pill or as minimal text, at the top or the
 *     bottom of the screen, at any size.
 *  2. Floating words: every word pops into the world as it is sung, where your crosshair points, and stays there.
 *     Words pile up and the whole set fades after a few seconds so a fresh one can start.
 */
public final class LyricsHud {
	private static final Ui UI = new Ui();

	// ---------- one lyric line, measured once ----------

	/** A line split into words with their timing fractions and pixel offsets (used for the karaoke sweep and for placing words). */
	private static final class Layout {
		final String text;
		final String[] words;
		final float[] starts;
		final int[] prefix, ww;
		final int lineW;

		Layout(TextRenderer tr, String text) {
			this.text = text;
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
			int space = Math.max(2, ClientFont.widthSmooth(tr, " "));
			int acc = 0;
			for (int i = 0; i < words.length; i++) {
				ww[i] = ClientFont.widthSmooth(tr, words[i]);
				prefix[i] = acc;
				acc += ww[i] + space;
			}
			lineW = Math.max(1, acc - space);
		}
	}

	private static final Layout EMPTY = new Layout(null, "");

	/** Everything the bar needs to draw one frame (the real song, or the sample shown in the settings preview). */
	private static final class Bar {
		Layout cur = EMPTY;
		String prevText = "", nextText = "", title = "", artist = "", message = null;
		float frac = 0f, trans = 1f, alpha = 1f;
		boolean playing = true, dots = false, hasSong = true;
		long posMs = 0L, durMs = 0L;
		long timeMs = 0L;
	}

	private static final class Word {
		double x, y, z;          // centre in the world
		double rx, rz;           // unit vector pointing to the reader's right (horizontal)
		String text;
		int w;                   // width in font pixels
		int idx;                 // running number (rainbow colouring)
		float frac;              // 0..1 position inside its line (gradient colouring)
		long born, die;
	}

	private static final List<Word> WORDS = new ArrayList<>();
	private static List<Lrc.Line> lastLines = null;
	private static int lineIdx = -2;
	private static int spawned = 0;
	private static Layout layout = EMPTY, prevLayout = EMPTY;
	private static long lineWall = 0L;
	private static float lineFrac = 0f;
	private static long groupEnd = 0L;
	private static int wordCounter = 0;
	private static String nextText = "";

	// sentence layout: where the current group is anchored (centre of the first row) and how many rows are used
	private static double ax, ay, az, arx, arz;
	private static int rows = 0;
	// per-word crosshair layout: the row being built
	private static Word lastWord = null;
	private static double rowLx, rowLy, rowLz, rowRx, rowRz, rowExtent;

	private static float barAlpha = 0f, barTotalH = 0f;
	private static final float[] BAR_W = {0f}, PREVIEW_W = {0f};
	/** Widest line of the current song so far (the bar grows to it once and then stays still), and the sideways scroll of long lines. */
	private static int songMaxW = 0;
	private static float textScroll = 0f;
	private static long lastNanos = 0L;
	private static long pausedSince = 0L;
	private static boolean lastBarBottom = false;
	private static float lastBarScale = 1f;

	private static final String[] NOTE = {
		"...#####", "...#...#", "...#...#", "...#...#", "...#...#", "..##..##", ".###.###", ".###.###", "..#...#."
	};
	private static final String[] PAUSE = {"##..##", "##..##", "##..##", "##..##", "##..##", "##..##", "##..##"};

	private LyricsHud() {}

	/** Pixels at the top of the screen the bar is using (notifications move below it). */
	public static int occupiedHeight() {
		RayConfig cfg = RayConfig.get();
		if (!cfg.lyricsOn || !cfg.lyricsBar || barAlpha < 0.05f || cfg.lyricsBarPos != 0) return 0;
		return Math.round(cfg.lyricsY + 4 + barTotalH * cfg.lyricsBarScale / 100f);
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
		if (cfg.lyricsBar) drawRealBar(ctx, mc, cfg, dt);
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
			layout = EMPTY;
			prevLayout = EMPTY;
			nextText = "";
			songMaxW = 0;
			textScroll = 0f;
		}
		if (lineIdx >= 0 && idx < lineIdx) { // the song was rewound or restarted
			clearWorld();
			lineIdx = -2;
		}
		if (idx != lineIdx) {
			prevLayout = layout;
			textScroll = 0f;
			lineIdx = idx;
			spawned = 0;
			lineWall = now;
			layout = new Layout(mc.textRenderer, idx >= 0 ? lines.get(idx).text() : "");
			// the next line that has words (instrumental gaps are skipped)
			nextText = "";
			for (int j = idx + 1; j < lines.size() && j <= idx + 3; j++) {
				if (!lines.get(j).text().isEmpty()) {
					nextText = lines.get(j).text();
					break;
				}
			}
		}
		if (idx < 0 || layout.words.length == 0) {
			lineFrac = 0f;
			return;
		}

		long lineStart = lines.get(idx).timeMs();
		float span = Lrc.singMs(lines, idx);
		lineFrac = Math.max(0f, Math.min(1f, (pos - lineStart) / span));

		if (!cfg.lyricsWorld || !Lyrics.playing) return;
		// joined a line late (new song, seek): do not dump a pile of old words at once
		while (spawned < layout.words.length && pos - (lineStart + layout.starts[spawned] * span) > 1500L) spawned++;
		while (spawned < layout.words.length && pos + 60L >= lineStart + layout.starts[spawned] * span) {
			spawnWord(mc, tc, cfg, spawned, now);
			spawned++;
		}
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
		Layout L = layout;
		Word w = new Word();
		w.text = L.words[k];
		w.w = L.ww[k];
		w.born = now;
		w.idx = ++wordCounter;
		w.frac = L.words.length > 1 ? k / (float) (L.words.length - 1) : 0f;

		// a new group (new anchor at your crosshair) only ever starts with a line, never in the middle of a sentence
		boolean fresh = WORDS.isEmpty() || (k == 0 && !groupAlive(now));
		if (!fresh && now >= groupEnd) groupEnd = now + 1800L;
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
			double off = (L.prefix[k] + L.ww[k] / 2.0 - L.lineW / 2.0) * bpp;
			w.x = ax + arx * off;
			w.z = az + arz * off;
			w.y = ay - rows * lineH;
			w.rx = arx;
			w.rz = arz;
		} else {
			// Per-word crosshair: the word lands where you are looking; if that spot is taken by the previous word
			// it continues the sentence to the right, and when the row is full it starts a new row below.
			double wBlocks = L.ww[k] * bpp;
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

	private static int wordColor(RayConfig cfg, Word w, long now) {
		int accent = Theme.accent();
		switch (cfg.lyricsColorMode) {
			case 1: {
				float hue = ((w.idx * 0.085f) + (now % 6000L) / 6000f) % 1f;
				return 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 0.55f, 1f) & 0xFFFFFF);
			}
			case 2: {
				int a = Theme.pick(cfg.lyricsColor, accent);
				int b = Theme.pick((cfg.lyricsColor + 7) % Theme.PALETTE_NAMES.length, accent);
				return Theme.mix(a, b, w.frac);
			}
			default:
				return Theme.pick(cfg.lyricsColor, accent);
		}
	}

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
		float[] a0 = new float[2], a1 = new float[2];

		for (Word w : WORDS) {
			long age = now - w.born;
			float in = Math.max(0f, Math.min(1f, age / (cfg.lyricsAnim == 2 ? 380f : 260f)));
			float out = now > w.die ? Math.max(0f, 1f - (now - w.die) / 500f) : 1f;
			float alpha = Math.min(Math.min(1f, in * 1.6f), out);
			if (alpha <= 0.01f) continue;
			if (!proj.project(w.x, w.y, w.z, a0)) continue;
			if (!proj.project(w.x + crx * 0.5, w.y, w.z + crz * 0.5, a1)) continue;
			float pxPerBlock = Math.abs(a1[0] - a0[0]) / 0.5f;

			float grow, rise = 0f;
			switch (cfg.lyricsAnim) {
				case 1 -> { // rise: drifts up into place
					grow = 0.9f + 0.1f * Theme.easeOutCubic(in);
					rise = (1f - Theme.easeOutCubic(in)) * 9f;
				}
				case 2 -> grow = 1f; // fade
				default -> grow = 0.55f + 0.45f * Theme.easeOutBack(in); // pop
			}
			float scale = (float) (pxPerBlock * bpp) * grow * (0.92f + 0.08f * out);
			if (scale < 0.25f || scale > 5f) continue;
			if (scale > 3.4f) alpha *= Math.max(0f, (5f - scale) / 1.6f); // very close: fade instead of towering over the screen
			if (alpha <= 0.01f) continue;
			if (a0[0] < -80 || a0[0] > sw + 80 || a0[1] < -40 || a0[1] > sh + 40) continue;

			// freshly sung words flash white, then settle into the chosen colour
			float flash = Math.max(0f, 1f - age / 650f);
			int base = wordColor(cfg, w, now);
			int col = Theme.mix(base, 0xFFFFFFFF, flash * 0.85f);
			int x0 = -w.w / 2, y0 = -4;
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate(a0[0], a0[1]);
			ctx.getMatrices().scale(scale, scale);
			ctx.getMatrices().translate(0f, rise);
			switch (cfg.lyricsWordStyle) {
				case 1 -> { // outline
					int o = Theme.fade(0xFF000000, 0.85f * alpha);
					ClientFont.drawSmooth(ctx, tr, w.text, x0 - 1, y0 - 1, o, false);
					ClientFont.drawSmooth(ctx, tr, w.text, x0 + 1, y0 - 1, o, false);
					ClientFont.drawSmooth(ctx, tr, w.text, x0 - 1, y0 + 1, o, false);
					ClientFont.drawSmooth(ctx, tr, w.text, x0 + 1, y0 + 1, o, false);
				}
				case 2 -> ClientFont.drawSmooth(ctx, tr, w.text, x0 + 1, y0 + 1, Theme.fade(0xFF000000, 0.55f * alpha), false); // clean
				default -> { // glow
					int glow = Theme.fade(base, 0.22f * alpha);
					ClientFont.drawSmooth(ctx, tr, w.text, x0 - 1, y0 - 1, glow, false);
					ClientFont.drawSmooth(ctx, tr, w.text, x0 + 1, y0 + 1, glow, false);
					ClientFont.drawSmooth(ctx, tr, w.text, x0 + 1, y0 + 1, Theme.fade(0xFF000000, 0.5f * alpha), false);
				}
			}
			ClientFont.drawSmooth(ctx, tr, w.text, x0, y0, Theme.fade(col, alpha), false);
			ctx.getMatrices().popMatrix();
		}
	}

	// ---------- the bar ----------

	private static void drawRealBar(DrawContext ctx, MinecraftClient mc, RayConfig cfg, float dt) {
		long now = System.currentTimeMillis();
		Lyrics.Status st = Lyrics.status;
		boolean have = Lyrics.spotifyFound && !Lyrics.title.isEmpty() && st != Lyrics.Status.IDLE;
		if (st == Lyrics.Status.MISSING && now - Lyrics.trackSince > 6000L) have = false;
		if (Lyrics.playing) pausedSince = 0L;
		else if (pausedSince == 0L) pausedSince = now;
		if (cfg.lyricsHidePaused && pausedSince != 0L && now - pausedSince > 6000L) have = false;
		float target = !have ? 0f : (Lyrics.playing ? 1f : 0.6f);
		barAlpha += (target - barAlpha) * (1f - (float) Math.exp(-dt * 9f));
		if (barAlpha < 0.01f) return;

		Bar b = new Bar();
		b.alpha = barAlpha;
		b.playing = Lyrics.playing;
		b.title = Lyrics.title;
		b.artist = Lyrics.artist;
		b.posMs = Lyrics.rawPositionMs();
		b.durMs = Lyrics.durationMs;
		b.timeMs = now;
		b.frac = st == Lyrics.Status.READY ? lineFrac : -1f;
		float t = Math.max(0f, Math.min(1f, (now - lineWall) / 340f));
		b.trans = cfg.uiAnimations ? Theme.easeOutCubic(t) : 1f;
		b.prevText = prevLayout.text;
		b.nextText = nextText;
		switch (st) {
			case LOADING -> b.message = "Finding lyrics...";
			case MISSING -> b.message = "No lyrics for this song";
			case ERROR -> b.message = "Lyrics unavailable - retrying";
			case AD -> b.message = "Advertisement";
			default -> {
				b.cur = layout;
				b.dots = layout.text.isEmpty();
			}
		}
		if (b.message != null) {
			b.cur = new Layout(mc.textRenderer, b.message);
			b.frac = -1f;
			b.nextText = "";
			b.prevText = "";
		}

		int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
		float sc = cfg.lyricsBarScale / 100f;
		lastBarScale = sc;
		int top;
		if (cfg.lyricsBarPos == 0) top = cfg.lyricsY;
		else top = Math.round(sh - cfg.lyricsY - 58 - barTotalH * sc);
		drawBar(ctx, mc.textRenderer, cfg, b, sw / 2, top, sc, sw, BAR_W, true);
	}

	/**
	 * The settings preview: the same bar, with a sample song that loops, drawn at (cx, top).
	 * Returns the height it used so the settings page can reserve room.
	 */
	public static int drawPreview(DrawContext ctx, TextRenderer tr, Ui ui, int cx, int top, int availW) {
		RayConfig cfg = RayConfig.get();
		long now = System.currentTimeMillis();
		String[] sample = {"Now if you know you're with somebody", "Take it slow, we're going nowhere fast"};
		int which = (int) ((now / 4200L) % sample.length);
		long local = now % 4200L;
		Bar b = new Bar();
		b.cur = new Layout(tr, sample[which]);
		b.prevText = sample[(which + 1) % sample.length];
		b.nextText = sample[(which + 1) % sample.length];
		b.title = "Song Name";
		b.artist = "Artist";
		b.frac = Math.min(1f, local / 3600f);
		b.trans = Theme.easeOutCubic(Math.min(1f, local / 340f));
		b.alpha = 1f;
		b.playing = true;
		b.posMs = 83_000L + (now % 60000L);
		b.durMs = 185_000L;
		b.timeMs = now;
		float sc = Math.min(cfg.lyricsBarScale / 100f, 1.4f);
		UI.begin(ctx, tr, -1, -1);
		return Math.round(drawBar(ctx, tr, cfg, b, cx, top, sc, availW, PREVIEW_W, false) * sc);
	}

	private static String clock(long ms) {
		long s = Math.max(0L, ms / 1000L);
		return (s / 60L) + ":" + String.format("%02d", s % 60L);
	}

	/** Draws the bar and returns its unscaled height. (cx, top) is the top centre. */
	private static float drawBar(DrawContext ctx, TextRenderer tr, RayConfig cfg, Bar b, int cx, int top, float sc, int screenW, float[] wState, boolean real) {
		boolean pill = cfg.lyricsBarStyle == 0;
		float A = b.alpha;
		int accent = Theme.accent();
		long now = b.timeMs;

		// ---- measure ----
		// The bar keeps one width for the whole song (it only grows when a longer line shows up) so it does not breathe from
		// line to line, and a line that is still too long scrolls sideways with the singing instead of shrinking.
		Layout cur = b.cur;
		int avail = Math.max(120, Math.round(screenW * 0.7f / sc)) - (pill ? 62 : 20);
		int tw = ClientFont.widthSmooth(tr, cur.text);
		if (real && b.message == null) songMaxW = Math.max(songMaxW, tw);
		int ref = real ? Math.max(songMaxW, 200) : Math.max(tw, 200);
		float s = 1f;
		int innerW = Math.max(120, Math.min(ref, avail));
		int targetW = pill ? 6 + 16 + 9 + innerW + 9 + 14 + 8 : innerW + 16;
		if (wState[0] <= 1f) wState[0] = targetW;
		wState[0] += (targetW - wState[0]) * 0.18f;
		int bw = Math.round(wState[0]);
		int pillH = pill ? 24 : 16;
		boolean showNext = cfg.lyricsNext && !b.nextText.isEmpty() && b.message == null;
		boolean showMeta = cfg.lyricsShowTitle && !b.title.isEmpty();
		float totalH = pillH + (showNext ? 14 : 0) + (showMeta ? 13 : 0) + (cfg.lyricsProgress && !pill ? 5 : 0);
		if (real) barTotalH = totalH;

		boolean scaled = Math.abs(sc - 1f) > 0.01f;
		if (scaled) {
			ctx.getMatrices().pushMatrix();
			ctx.getMatrices().translate((float) cx, (float) top);
			ctx.getMatrices().scale(sc, sc);
			cx = 0;
			top = 0;
		}

		int bx = cx - bw / 2, by = top;
		float e = b.trans;

		if (pill) {
			UI.shadow(bx, by, bw, pillH, 11);
			UI.panel(bx, by, bw, pillH, 11, Theme.fade(0xFF0E1015, 0.93f * A), Theme.fade(Theme.mix(Theme.STROKE, accent, 0.35f), A));
			// play / pause tile
			UI.rrect(bx + 4, by + 4, 16, 16, 6, Theme.fade(accent, A));
			if (b.playing) UI.pix(NOTE, bx + 8, by + 7, 1, Theme.fade(0xFFFFFFFF, A));
			else UI.pix(PAUSE, bx + 9, by + 8, 1, Theme.fade(0xFFFFFFFF, A));
			// progress line along the bottom inside the pill
			if (cfg.lyricsProgress && b.durMs > 0) {
				int px0 = bx + 14, pw = bw - 28;
				float p = Math.max(0f, Math.min(1f, b.posMs / (float) b.durMs));
				UI.rrect(px0, by + pillH - 4, pw, 2, 1, Theme.fade(0xFFFFFFFF, 0.10f * A));
				UI.rrect(px0, by + pillH - 4, Math.max(2, Math.round(pw * p)), 2, 1, Theme.fade(accent, 0.9f * A));
			}
		}

		// ---- the line ----
		int areaX = pill ? bx + 6 + 16 + 9 : bx + 8;
		int areaW = pill ? bw - (6 + 16 + 9) - (9 + 14 + 8) : bw - 16;
		int ty = by + (pill ? 6 : 3);
		if (areaW > 8) {
			ctx.enableScissor(areaX, by - 2, areaX + areaW, by + pillH + 2);
			if (e < 0.98f && !b.prevText.isEmpty() && b.message == null) {
				drawLine(ctx, tr, b.prevText, null, areaX, areaW, ty - Math.round(e * 8f), 1f, Theme.fade(Theme.DIM, (1f - e) * 0.8f * A), -1f, accent, 0f, !pill);
			}
			if (b.dots) {
				for (int i = 0; i < 3; i++) {
					float pulse = 0.5f + 0.5f * (float) Math.sin(now / 260.0 - i * 0.9);
					UI.circle(areaX + areaW / 2 - 8 + i * 8, by + pillH / 2 - (pill ? 1 : 0), 2, Theme.fade(accent, (0.35f + 0.65f * pulse) * A * e));
				}
			} else {
				int dim = b.message != null ? Theme.fade(Theme.DIM, 0.9f * A * e) : Theme.fade(Theme.TEXT, 0.5f * A * e);
				drawLine(ctx, tr, cur.text, cur, areaX, areaW, ty + Math.round((1f - e) * 8f), s, dim, b.frac, accent, A * e, !pill);
			}
			ctx.disableScissor();
		}

		// equaliser
		if (pill) {
			int ex = bx + bw - 8 - 14, base = by + pillH - 8;
			for (int i = 0; i < 4; i++) {
				int h = b.playing ? 3 + Math.round(8f * Math.abs((float) Math.sin(now / 210.0 + i * 1.9))) : 3;
				UI.rrect(ex + i * 4, base - h + 1, 2, h, 1, Theme.fade(Theme.mix(accent, 0xFFFFFFFF, 0.2f), 0.9f * A));
			}
		} else if (cfg.lyricsProgress && b.durMs > 0) {
			int pw = Math.min(bw - 16, 140);
			int px0 = cx - pw / 2;
			float p = Math.max(0f, Math.min(1f, b.posMs / (float) b.durMs));
			UI.rrect(px0, by + pillH + 2, pw, 2, 1, Theme.fade(0xFFFFFFFF, 0.14f * A));
			UI.rrect(px0, by + pillH + 2, Math.max(2, Math.round(pw * p)), 2, 1, Theme.fade(accent, 0.9f * A));
		}

		// ---- under the bar ----
		int y2 = by + pillH + (cfg.lyricsProgress && !pill ? 6 : 3);
		if (showNext) {
			String nx = fitSmooth(tr, b.nextText, Math.round(screenW * 0.6f / sc));
			float ns = 0.85f;
			int nw = Math.round(ClientFont.widthSmooth(tr, nx) * ns);
			drawScaled(ctx, tr, nx, cx - nw / 2, y2 + 1, ns, Theme.fade(0xFFFFFFFF, 0.42f * A * (0.6f + 0.4f * e)));
			y2 += 14;
		}
		if (showMeta) {
			String info = b.artist.isEmpty() ? b.title : b.artist + " - " + b.title;
			String time = cfg.lyricsProgress && b.durMs > 0 ? "   " + clock(b.posMs) + " / " + clock(b.durMs) : "";
			info = fitSmooth(tr, info, Math.max(80, Math.round(screenW * 0.5f / sc) - ClientFont.widthSmooth(tr, time)));
			float is = 0.8f;
			int iw = Math.round(ClientFont.widthSmooth(tr, info + time) * is);
			int ix = cx - iw / 2;
			drawScaled(ctx, tr, info, ix, y2 + 1, is, Theme.fade(Theme.DIM, 0.9f * A));
			if (!time.isEmpty()) drawScaled(ctx, tr, time, ix + Math.round(ClientFont.widthSmooth(tr, info) * is), y2 + 1, is, Theme.fade(Theme.MUTE, 0.9f * A));
		}

		if (scaled) ctx.getMatrices().popMatrix();
		return totalH;
	}

	private static String fitSmooth(TextRenderer tr, String s, int maxW) {
		while (s.length() > 4 && ClientFont.widthSmooth(tr, s) > maxW) s = s.substring(0, s.length() - 2).trim() + "...";
		return s;
	}

	/**
	 * Draws a line centred in the text area. With sungFrac >= 0 and a layout, the part already sung is painted bright
	 * (karaoke). A line wider than the area is not shrunk: it scrolls so the word being sung stays in view.
	 */
	private static void drawLine(DrawContext ctx, TextRenderer tr, String text, Layout lay, int areaX, int areaW, int y, float s, int dim,
								 float sungFrac, int accent, float litAlpha, boolean shadowed) {
		int tw = Math.round(ClientFont.widthSmooth(tr, text) * s);
		int tx = areaX + (areaW - tw) / 2;
		float sungPx = 0f;
		if (lay != null && lay.words.length > 0 && sungFrac >= 0f) {
			int k = 0;
			for (int i = 0; i < lay.starts.length; i++) if (lay.starts[i] <= sungFrac) k = i;
			float next = k + 1 < lay.starts.length ? lay.starts[k + 1] : 1f;
			float inWord = Math.max(0f, Math.min(1f, (sungFrac - lay.starts[k]) / Math.max(0.001f, next - lay.starts[k])));
			sungPx = sungFrac >= 1f ? lay.lineW : lay.prefix[k] + inWord * lay.ww[k];
		}
		if (tw > areaW) {
			boolean live = litAlpha > 0f; // only the current line scrolls; the line sliding out stays put
			float target = live ? Math.max(0f, Math.min(tw - areaW, sungPx * s - areaW * 0.5f)) : textScroll;
			if (live) textScroll += (target - textScroll) * 0.12f;
			tx = areaX - Math.round(live ? textScroll : target);
		}
		if (shadowed) drawScaled(ctx, tr, text, tx + 1, y + 1, s, Theme.fade(0xFF000000, 0.55f * (dim >>> 24) / 255f));
		drawScaled(ctx, tr, text, tx, y, s, dim);
		if (sungFrac < 0f || lay == null || lay.words.length == 0) return;

		int clipW = Math.round(sungPx * s);
		if (clipW <= 0) return;
		ctx.enableScissor(Math.max(areaX, tx), y - 3, Math.min(areaX + areaW, tx + clipW), y + 14);
		drawScaled(ctx, tr, text, tx, y, s, Theme.fade(0xFFFFFFFF, litAlpha));
		UI.rect(tx, y + 11, clipW, 1, Theme.fade(accent, 0.7f * litAlpha));
		ctx.disableScissor();
	}

	private static void drawScaled(DrawContext ctx, TextRenderer tr, String text, int x, int y, float s, int color) {
		if (Math.abs(s - 1f) < 0.01f) {
			ClientFont.drawSmooth(ctx, tr, text, x, y, color, false);
			return;
		}
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().translate((float) x, (float) y);
		ctx.getMatrices().scale(s, s);
		ClientFont.drawSmooth(ctx, tr, text, 0, 0, color, false);
		ctx.getMatrices().popMatrix();
	}
}
