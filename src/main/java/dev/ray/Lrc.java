package dev.ray;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads synced lyrics in the common LRC format ("[01:23.45] some words"). Pure Java, no Minecraft classes. */
public final class Lrc {
	public record Line(long timeMs, String text) {}

	private static final Pattern TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

	private Lrc() {}

	/** Parses LRC text into lines sorted by time. Empty lines are kept (they mark instrumental gaps). */
	public static List<Line> parse(String lrc) {
		List<Line> out = new ArrayList<>();
		if (lrc == null) return out;
		for (String raw : lrc.split("\\r?\\n")) {
			Matcher m = TAG.matcher(raw);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (m.find() && m.start() == end) {
				long min = Long.parseLong(m.group(1));
				long sec = Long.parseLong(m.group(2));
				long ms = 0;
				String f = m.group(3);
				if (f != null) {
					ms = Long.parseLong(f);
					if (f.length() == 1) ms *= 100;
					else if (f.length() == 2) ms *= 10;
				}
				times.add(min * 60_000L + sec * 1000L + ms);
				end = m.end();
			}
			if (times.isEmpty()) continue;
			String text = clean(raw.substring(end));
			for (long t : times) out.add(new Line(t, text));
		}
		out.sort(Comparator.comparingLong(Line::timeMs));
		return out;
	}

	/** Lyrics without timestamps: spread the lines evenly over the song so something sensible still shows. */
	public static List<Line> fromPlain(String plain, long durationMs) {
		List<Line> out = new ArrayList<>();
		if (plain == null || durationMs <= 0) return out;
		List<String> texts = new ArrayList<>();
		for (String raw : plain.split("\\r?\\n")) {
			String t = clean(raw);
			if (!t.isEmpty()) texts.add(t);
		}
		if (texts.isEmpty()) return out;
		long start = Math.min(8000L, durationMs / 10);
		long span = Math.max(1000L, durationMs - start - 6000L);
		for (int i = 0; i < texts.size(); i++) out.add(new Line(start + span * i / texts.size(), texts.get(i)));
		return out;
	}

	private static String clean(String s) {
		return s.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	/** Index of the line that is being sung at posMs (the last line starting at or before it), or -1 before the first. */
	public static int indexAt(List<Line> lines, long posMs) {
		int lo = 0, hi = lines.size() - 1, ans = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= posMs) {
				ans = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return ans;
	}

	/** How long line i lasts, capped so a long instrumental gap does not stretch the words. */
	public static long durationOf(List<Line> lines, int i) {
		long start = lines.get(i).timeMs();
		long next = i + 1 < lines.size() ? lines.get(i + 1).timeMs() : start + 5000L;
		return Math.max(600L, Math.min(next - start, 9000L));
	}

	/** Start of every word as a 0..1 fraction of the line, weighted by word length so long words take longer. */
	public static float[] wordStarts(String[] words) {
		float[] w = new float[words.length];
		float total = 0f;
		for (int i = 0; i < words.length; i++) {
			w[i] = Math.max(2, words[i].length()) + 1.5f;
			total += w[i];
		}
		float[] starts = new float[words.length];
		float acc = 0f;
		for (int i = 0; i < words.length; i++) {
			starts[i] = acc / total;
			acc += w[i];
		}
		return starts;
	}
}
