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
	public enum Status { IDLE, LOADING, READY, MISSING, ERROR, AD }

	public static volatile Status status = Status.IDLE;
	public static volatile String artist = "", title = "";
	public static volatile List<Lrc.Line> lines = List.of();
	public static volatile boolean synced = false;
	public static volatile boolean spotifyFound = false;
	public static volatile boolean playing = false;
	public static volatile String platformNote = "";
	/** Wall-clock time the current song was first seen (used for the "no lyrics" message timeout). */
	public static volatile long trackSince = 0L;
	/** Length of the song in milliseconds (0 when the player does not tell us). */
	public static volatile long durationMs = 0L;

	private static volatile long baseMs = 0L, baseAt = 0L;
	private static volatile String trackKey = "";
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
	private static long enabledSince = 0L;
	private static boolean hinted = false;

	public static void tick() {
		wantRun = RayConfig.get().lyricsOn;
		if (!wantRun) {
			enabledSince = 0L;
			hinted = false;
		} else {
			long now = System.currentTimeMillis();
			if (enabledSince == 0L) enabledSince = now;
			if (!hinted && now - enabledSince > 6000L && !spotifyFound) {
				hinted = true;
				Toasts.push("Lyric Sync: open Spotify and play a song");
			}
		}
		if (wantRun && (watcher == null || !watcher.isAlive())) {
			watcher = new Thread(Lyrics::watchLoop, "Ray-LyricSync");
			watcher.setDaemon(true);
			watcher.start();
		}
	}

	public static long positionMs() {
		long pos = playing ? baseMs + (System.currentTimeMillis() - baseAt) : baseMs;
		return pos + RayConfig.get().lyricsOffset + songOffset();
	}

	/** The raw playback position (no sync corrections), for the progress bar. */
	public static long rawPositionMs() {
		return playing ? baseMs + (System.currentTimeMillis() - baseAt) : baseMs;
	}

	private static String offsetKey() {
		return (artist + " - " + title).toLowerCase();
	}

	/** The correction remembered for the song that is playing. */
	public static int songOffset() {
		Integer v = RayConfig.get().lyricsSongOffsets.get(offsetKey());
		return v == null ? 0 : v;
	}

	/** Moves the lyrics earlier (negative) or later (positive) for this song only, and remembers it. */
	public static void nudge(int ms) {
		if (title.isEmpty()) return;
		RayConfig cfg = RayConfig.get();
		int v = Math.max(-8000, Math.min(8000, songOffset() + ms));
		if (v == 0) cfg.lyricsSongOffsets.remove(offsetKey());
		else cfg.lyricsSongOffsets.put(offsetKey(), v);
		RayConfig.save();
		Toasts.push("Lyrics " + (v >= 0 ? "+" : "") + v + " ms for this song");
	}

	public static void resetSongOffset() {
		RayConfig.get().lyricsSongOffsets.remove(offsetKey());
		RayConfig.save();
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
				if (!wantRun) stopWinReader();
				if (wantRun) {
					Info info = read();
					spotifyFound = info != null;
					if (info != null) onInfo(info);
					else playing = false;
				}
				Thread.sleep(wantRun ? 400L : 1500L);
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
			final long token = ++fetchToken;
			if (isAd(i.artist(), i.title())) {
				status = Status.AD;
				return;
			}
			status = Status.LOADING;
			startFetch(i.artist(), i.title(), i.durMs(), token);
			return;
		}
		if (i.durMs() > 0) durationMs = i.durMs();
		// the lyrics service was unreachable: try again every 15 seconds while the same song plays
		if (status == Status.ERROR && now - lastFetchAt > 15_000L) {
			status = Status.LOADING;
			startFetch(artist, title, durationMs, ++fetchToken);
		}
		if (i.posMs() >= 0) {
			// exact position from the player: only correct the clock when it has drifted
			long expected = playing ? baseMs + (now - baseAt) : baseMs;
			if (Math.abs(expected - i.posMs()) > 600L || playing != i.playing()) {
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

	// Windows: one long-lived PowerShell asks the system media session for Spotify (artist, title, play state and the exact
	// position). If that is not available it falls back to the Spotify window title. Starting PowerShell once instead of
	// every second keeps the game smooth.
	private static final String WIN_SCRIPT = """
		$ErrorActionPreference='SilentlyContinue'
		[Console]::OutputEncoding=[Text.Encoding]::UTF8
		$mgr=$null; $asTask=$null
		try {
		 Add-Type -AssemblyName System.Runtime.WindowsRuntime
		 $asTask=([System.WindowsRuntimeSystemExtensions].GetMethods()|Where-Object{$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'})[0]
		 [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
		 [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]
		 $m=[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()
		 $t=$asTask.MakeGenericMethod([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]).Invoke($null,@($m)); $t.Wait(5000)|Out-Null; $mgr=$t.Result
		} catch {}
		while($true){
		 $line=''
		 try{
		  if($mgr -ne $null){
		   foreach($s in $mgr.GetSessions()){
		    if($s.SourceAppUserModelId -like '*Spotify*'){
		     $pt=$asTask.MakeGenericMethod([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]).Invoke($null,@($s.TryGetMediaPropertiesAsync()))
		     $pt.Wait(2000)|Out-Null; $p=$pt.Result
		     $tl=$s.GetTimelineProperties(); $st=$s.GetPlaybackInfo().PlaybackStatus.ToString()
		     $pos=$tl.Position.TotalMilliseconds
		     if($st -eq 'Playing'){ $pos+=([DateTimeOffset]::Now-$tl.LastUpdatedTime).TotalMilliseconds }
		     $line='S||'+$p.Artist+'||'+$p.Title+'||'+$st+'||'+[int64]$pos+'||'+[int64]$tl.EndTime.TotalMilliseconds
		     break
		    }
		   }
		  }
		 }catch{}
		 if($line -eq ''){ $w=(Get-Process -Name Spotify | Where-Object{$_.MainWindowTitle} | Select-Object -First 1).MainWindowTitle; if($w){$line='W||'+$w} }
		 [Console]::WriteLine($line)
		 Start-Sleep -Milliseconds 350
		}
		""";

	private static Process winProc;
	private static volatile String winLine = "";
	private static volatile long winLineAt = 0L;

	private static void ensureWinReader() throws Exception {
		if (winProc != null && winProc.isAlive()) return;
		String enc = java.util.Base64.getEncoder().encodeToString(WIN_SCRIPT.getBytes(StandardCharsets.UTF_16LE));
		winProc = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc)
			.redirectErrorStream(false).start();
		final Process p = winProc;
		Runtime.getRuntime().addShutdownHook(new Thread(p::destroyForcibly));
		Thread t = new Thread(() -> {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				String ln;
				while ((ln = r.readLine()) != null) {
					winLine = ln.trim();
					winLineAt = System.currentTimeMillis();
				}
			} catch (Exception ignored) {
			}
		}, "Ray-LyricSync-Reader");
		t.setDaemon(true);
		t.start();
	}

	private static void stopWinReader() {
		if (winProc != null) {
			winProc.destroyForcibly();
			winProc = null;
		}
		winLine = "";
	}

	private static Info readWindows() throws Exception {
		ensureWinReader();
		String out = winLine;
		if (System.currentTimeMillis() - winLineAt > 6000L) out = ""; // reader stalled: treat as nothing playing
		if (out.startsWith("S||")) {
			platformNote = "Windows: exact position from the system media session";
			Info i = parseTail(out.substring(3), 1.0, 1.0);
			if (i != null) return i;
		}
		platformNote = "Windows: song comes from the Spotify window title";
		if (out.startsWith("W||")) out = out.substring(3);
		else if (out.isEmpty() || out.startsWith("S||")) return null; // Spotify is not running
		boolean paused = false;
		String lower = out.toLowerCase();
		if (lower.startsWith("paused")) { // "Paused · Song"
			paused = true;
			out = out.replaceFirst("(?i)^paused\\s*[\u00B7\\-:|]\\s*", "");
		}
		int dash = out.indexOf(" - ");
		if (dash <= 0) {
			if (paused && !out.isBlank()) return new Info(artist, out.trim(), false, -1L, 0L);
			// just "Spotify" / "Spotify Free" / "Spotify Premium": nothing is playing right now
			return new Info(artist, title, false, -1L, 0L);
		}
		return new Info(out.substring(0, dash).trim(), out.substring(dash + 3).trim(), !paused, -1L, 0L);
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
		if (res.statusCode() >= 500 || res.statusCode() == 429) throw new java.io.IOException("lyrics service busy: " + res.statusCode());
		return res.statusCode() == 200 ? res.body() : null;
	}

	/** "Song - 2011 Remaster", "Song (feat. X)" -> "Song". */
	static String cleanTitle(String t) {
		String s = t.replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "");
		s = s.replaceAll("(?i)\\s+-\\s+(\\d{4}\\s+)?(remaster|live|radio|single|version|mono|stereo|edit|remix|acoustic).*$", "");
		return s.trim().isEmpty() ? t : s.trim();
	}

	private static volatile long lastFetchAt = 0L;

	static boolean isAd(String art, String name) {
		String t = name == null ? "" : name.toLowerCase();
		String a = art == null ? "" : art.toLowerCase();
		return t.equals("advertisement") || t.equals("spotify") || a.equals("spotify") && t.isEmpty();
	}

	private static void startFetch(String art, String name, long durMs, long token) {
		lastFetchAt = System.currentTimeMillis();
		Thread t = new Thread(() -> fetch(art, name, durMs, token), "Ray-LyricFetch");
		t.setDaemon(true);
		t.start();
	}

	// ---- small on-disk cache so a song you already played shows its lyrics instantly (and offline) ----

	private static java.nio.file.Path cacheFile(String art, String name) {
		try {
			java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
			byte[] h = md.digest((art + "\u0000" + name).toLowerCase().getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : h) sb.append(String.format("%02x", b));
			return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("ray-lyrics").resolve(sb + ".json");
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean loadCache(String art, String name, long token) {
		if (!RayConfig.get().lyricsCache) return false;
		try {
			java.nio.file.Path f = cacheFile(art, name);
			if (f == null || !java.nio.file.Files.exists(f)) return false;
			JsonObject o = JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
			List<Lrc.Line> parsed = Lrc.parse(str(o, "lrc"));
			if (parsed.isEmpty()) return false;
			if (token != fetchToken) return true;
			synced = o.has("synced") && o.get("synced").getAsBoolean();
			lines = parsed;
			status = Status.READY;
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	private static void saveCache(String art, String name, List<Lrc.Line> result, boolean isSynced) {
		if (!RayConfig.get().lyricsCache || result.isEmpty()) return;
		try {
			java.nio.file.Path f = cacheFile(art, name);
			if (f == null) return;
			java.nio.file.Files.createDirectories(f.getParent());
			StringBuilder sb = new StringBuilder();
			for (Lrc.Line l : result) {
				long m = l.timeMs() / 60000L, sec = (l.timeMs() / 1000L) % 60L, cs = (l.timeMs() % 1000L) / 10L;
				sb.append(String.format("[%02d:%02d.%02d]%s%n", m, sec, cs, l.text()));
			}
			JsonObject o = new JsonObject();
			o.addProperty("synced", isSynced);
			o.addProperty("lrc", sb.toString());
			java.nio.file.Files.writeString(f, o.toString());
			// keep the folder small: remove the oldest files beyond 400
			try (var st = java.nio.file.Files.list(f.getParent())) {
				List<java.nio.file.Path> all = st.sorted(java.util.Comparator.comparingLong(p -> p.toFile().lastModified())).toList();
				for (int i = 0; i < all.size() - 400; i++) java.nio.file.Files.deleteIfExists(all.get(i));
			}
		} catch (Throwable ignored) {
		}
	}

	private static void fetch(String art, String name, long durMs, long token) {
		if (loadCache(art, name, token)) return;
		List<Lrc.Line> result = List.of();
		boolean isSynced = false;
		boolean networkFailed = false;
		try {
			JsonObject best = null;
			String body = get("https://lrclib.net/api/get?artist_name=" + enc(art) + "&track_name=" + enc(name)
				+ (durMs > 0 ? "&duration=" + (durMs / 1000) : ""));
			if (body != null) best = JsonParser.parseString(body).getAsJsonObject();
			if (best == null || !hasLyrics(best)) {
				String sb = get("https://lrclib.net/api/search?track_name=" + enc(cleanTitle(name)) + "&artist_name=" + enc(art));
				if (sb == null) sb = get("https://lrclib.net/api/search?q=" + enc(cleanTitle(name) + " " + art));
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
		} catch (java.io.IOException | InterruptedException e) {
			networkFailed = true;
		} catch (Throwable ignored) {
			// unexpected answer: treated as "no lyrics"
		}
		if (token != fetchToken) return; // the song changed again while we were loading
		synced = isSynced;
		lines = result;
		if (!result.isEmpty()) {
			status = Status.READY;
			saveCache(art, name, result, isSynced);
		} else {
			status = networkFailed ? Status.ERROR : Status.MISSING;
		}
	}

	private static boolean hasLyrics(JsonObject o) {
		return !str(o, "syncedLyrics").isEmpty() || !str(o, "plainLyrics").isEmpty();
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
}
