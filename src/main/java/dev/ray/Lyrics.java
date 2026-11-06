package dev.ray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Lyric Sync: finds out which song Spotify is playing, downloads its time-synced lyrics (from the free LRCLIB service,
 * no account or key needed) and keeps a clock of where in the song you are.
 *
 *  - Windows: reads the title of the Spotify window ("Artist - Song"). Spotify does not tell other programs the playback
 *    position, so the clock starts when the song changes. If it is a bit off, use the Sync Offset setting or the
 *    "Resync Lyrics" key (press it the moment a song starts).
 *  - Linux: uses playerctl (exact position). macOS: uses AppleScript (exact position).
 *
 * Everything slow (running programs, web requests) happens on a background thread, never on the game thread.
 */
public final class Lyrics {
	public enum Status { IDLE, LOADING, READY, MISSING }

	public static volatile Status status = Status.IDLE;
	public static volatile String artist = "", title = "";
	public static volatile List<Lrc.Line> lines = List.of();
	public static volatile boolean synced = false;
	public static volatile boolean spotifyFound = false;
	public static volatile boolean playing = false;
	public static volatile String platformNote = "";
	/** Wall-clock time the current song was first seen (used for the "no lyrics" message timeout). */
	public static volatile long trackSince = 0L;

	private static volatile long baseMs = 0L, baseAt = 0L;
	private static volatile String trackKey = "";
	private static volatile long durationMs = 0L;
	private static Thread watcher;
	private static volatile boolean wantRun = false;
	private static volatile long fetchToken = 0L;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();

	private Lyrics() {}

	private static boolean windows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	private static boolean mac() {
		return System.getProperty("os.name", "").toLowerCase().contains("mac");
	}

	/** Called every client tick: starts or stops the background watcher with the module switch. */
	public static void tick() {
		wantRun = RayConfig.get().lyricsOn;
		if (wantRun && (watcher == null || !watcher.isAlive())) {
			watcher = new Thread(Lyrics::watchLoop, "Ray-LyricSync");
			watcher.setDaemon(true);
			watcher.start();
		}
	}

	public static long positionMs() {
		long pos = playing ? baseMs + (System.currentTimeMillis() - baseAt) : baseMs;
		return pos + RayConfig.get().lyricsOffset;
	}

	/** Pretend the song started right now (for Windows, where the position is unknown). */
	public static void resync() {
		baseMs = 0L;
		baseAt = System.currentTimeMillis();
		playing = true;
		Toasts.push("Lyrics resynced");
	}

	// ---------- background loop ----------

	private record Info(String artist, String title, boolean playing, long posMs, long durMs) {}

	private static void watchLoop() {
		while (true) {
			try {
				if (wantRun) {
					Info info = read();
					spotifyFound = info != null;
					if (info != null) onInfo(info);
					else playing = false;
				}
				Thread.sleep(wantRun ? (windows() ? 1200L : 800L) : 1500L);
			} catch (InterruptedException e) {
				return;
			} catch (Throwable t) {
				try {
					Thread.sleep(3000L);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	private static void onInfo(Info i) {
		long now = System.currentTimeMillis();
		String key = (i.artist() + "\u0000" + i.title()).toLowerCase();
		if (!key.equals(trackKey)) {
			trackKey = key;
			artist = i.artist();
			title = i.title();
			trackSince = now;
			durationMs = i.durMs();
			baseMs = Math.max(0L, i.posMs());
			baseAt = now;
			playing = i.playing();
			lines = List.of();
			status = Status.LOADING;
			final long token = ++fetchToken;
			Thread t = new Thread(() -> fetch(i.artist(), i.title(), i.durMs(), token), "Ray-LyricFetch");
			t.setDaemon(true);
			t.start();
			return;
		}
		if (i.durMs() > 0) durationMs = i.durMs();
		if (i.posMs() >= 0) {
			// exact position from the player: only correct the clock when it has drifted
			long expected = playing ? baseMs + (now - baseAt) : baseMs;
			if (Math.abs(expected - i.posMs()) > 350L || playing != i.playing()) {
				baseMs = i.posMs();
				baseAt = now;
			}
			playing = i.playing();
		} else if (playing != i.playing()) {
			// no position info: freeze the clock while paused, continue after
			if (playing) baseMs = baseMs + (now - baseAt);
			baseAt = now;
			playing = i.playing();
		}
	}

	// ---------- reading Spotify ----------

	private static Info read() throws Exception {
		if (windows()) return readWindows();
		if (mac()) return readMac();
		return readLinux();
	}

	private static String run(long timeoutMs, String... cmd) throws Exception {
		Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
		StringBuilder sb = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
			String ln;
			while ((ln = r.readLine()) != null) sb.append(ln).append('\n');
		}
		if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) p.destroyForcibly();
		return sb.toString().trim();
	}

	private static Info readWindows() throws Exception {
		platformNote = "Windows: song comes from the Spotify window title";
		String out = run(5000L, "powershell", "-NoProfile", "-NonInteractive", "-Command",
			"[Console]::OutputEncoding=[Text.Encoding]::UTF8; (Get-Process -Name Spotify -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle } | Select-Object -First 1).MainWindowTitle");
		if (out.isEmpty()) return null; // Spotify is not running
		int dash = out.indexOf(" - ");
		if (dash <= 0) {
			// title is just "Spotify" / "Spotify Free" / "Spotify Premium": nothing is playing right now
			return new Info(artist, title, false, -1L, 0L);
		}
		return new Info(out.substring(0, dash).trim(), out.substring(dash + 3).trim(), true, -1L, 0L);
	}

	private static Info readLinux() throws Exception {
		platformNote = "Linux: needs 'playerctl' installed";
		String out = run(3000L, "playerctl", "-p", "spotify", "metadata", "--format",
			"{{artist}}||{{title}}||{{status}}||{{position}}||{{mpris:length}}");
		return parseTail(out, 1000.0, 1000.0);
	}

	private static Info readMac() throws Exception {
		platformNote = "macOS: uses AppleScript";
		String out = run(3000L, "osascript", "-e",
			"if application \"Spotify\" is running then tell application \"Spotify\" to if player state is not stopped then return (artist of current track) & \"||\" & (name of current track) & \"||\" & (player state as string) & \"||\" & (player position as string) & \"||\" & (duration of current track as string)");
		// position is in seconds, duration already in milliseconds
		return parseTail(out, 0.0, 0.0);
	}

	/** Parses "artist||title||status||position||length". posDiv/lenDiv of 0 mean "seconds" / "milliseconds" (macOS). */
	private static Info parseTail(String out, double posDiv, double lenDiv) {
		if (out == null || out.isEmpty() || !out.contains("||")) return null;
		String[] p = out.split("\\|\\|", -1);
		if (p.length < 3 || p[1].isBlank()) return null;
		boolean play = p[2].trim().equalsIgnoreCase("playing");
		long pos = -1L, dur = 0L;
		try {
			if (p.length > 3 && !p[3].isBlank()) {
				double v = Double.parseDouble(p[3].trim().replace(',', '.'));
				pos = posDiv == 0.0 ? (long) (v * 1000.0) : (long) (v / posDiv); // playerctl: microseconds -> ms
			}
			if (p.length > 4 && !p[4].isBlank()) {
				double v = Double.parseDouble(p[4].trim().replace(',', '.'));
				dur = lenDiv == 0.0 ? (long) v : (long) (v / lenDiv);
			}
		} catch (NumberFormatException ignored) {
		}
		return new Info(p[0].trim(), p[1].trim(), play, pos, dur);
	}

	// ---------- fetching lyrics ----------

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String get(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
			.header("User-Agent", "RayClient/" + RayClient.VERSION + " (Minecraft Fabric mod)").GET().build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return res.statusCode() == 200 ? res.body() : null;
	}

	/** "Song - 2011 Remaster", "Song (feat. X)" -> "Song". */
	static String cleanTitle(String t) {
		String s = t.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "");
		s = s.replaceAll("(?i)\\s+-\\s+(\\d{4}\\s+)?(remaster|live|radio|single|version|mono|stereo|edit|remix|acoustic).*$", "");
		return s.trim().isEmpty() ? t : s.trim();
	}

	private static void fetch(String art, String name, long durMs, long token) {
		List<Lrc.Line> result = List.of();
		boolean isSynced = false;
		try {
			JsonObject best = null;
			String body = get("https://lrclib.net/api/get?artist_name=" + enc(art) + "&track_name=" + enc(name)
				+ (durMs > 0 ? "&duration=" + (durMs / 1000) : ""));
			if (body != null) best = JsonParser.parseString(body).getAsJsonObject();
			if (best == null || !hasLyrics(best)) {
				String sb = get("https://lrclib.net/api/search?track_name=" + enc(cleanTitle(name)) + "&artist_name=" + enc(art));
				if (sb != null) {
					JsonArray arr = JsonParser.parseString(sb).getAsJsonArray();
					JsonObject plain = null;
					for (JsonElement e : arr) {
						JsonObject o = e.getAsJsonObject();
						if (str(o, "syncedLyrics").length() > 0) {
							best = o;
							plain = null;
							break;
						}
						if (plain == null && str(o, "plainLyrics").length() > 0) plain = o;
					}
					if (plain != null && (best == null || !hasLyrics(best))) best = plain;
				}
			}
			if (best != null) {
				String s = str(best, "syncedLyrics");
				if (!s.isEmpty()) {
					result = Lrc.parse(s);
					isSynced = !result.isEmpty();
				}
				if (result.isEmpty()) {
					long dur = best.has("duration") && !best.get("duration").isJsonNull() ? (long) (best.get("duration").getAsDouble() * 1000) : durMs;
					result = Lrc.fromPlain(str(best, "plainLyrics"), dur > 0 ? dur : 180_000L);
				}
			}
		} catch (Throwable ignored) {
			// network down or bad answer: treated as "no lyrics"
		}
		if (token != fetchToken) return; // the song changed again while we were loading
		synced = isSynced;
		lines = result;
		status = result.isEmpty() ? Status.MISSING : Status.READY;
	}

	private static boolean hasLyrics(JsonObject o) {
		return !str(o, "syncedLyrics").isEmpty() || !str(o, "plainLyrics").isEmpty();
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
}
