package dev.ray;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Keeps Ray Client up to date from the GitHub Releases of {@value #REPO}.
 *
 *  - About every six hours (and on request) it asks GitHub for the latest release. Nothing about you is sent.
 *  - In Auto mode it downloads the new jar in the background; in Notify mode it only tells you.
 *  - A download is only accepted if it comes from GitHub over HTTPS, has the promised size and checksum, and really is
 *    Ray Client (its fabric.mod.json is read and must name the mod "ray" at the version of the release).
 *  - The new jar is installed when you close the game (a running jar cannot be replaced). Your old jar is kept in
 *    mods/ray-backup so you can go back. If anything goes wrong, the old version simply stays in place.
 */
public final class Updater {
	public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, ERROR }

	public static final String REPO = "Client-ui/RayClient";
	public static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases";
	private static final long CHECK_EVERY_MS = 6L * 60 * 60 * 1000;
	private static final long MAX_BYTES = 64L * 1024 * 1024;

	public static volatile State state = State.IDLE;
	public static volatile String latest = "";
	public static volatile String notes = "";
	public static volatile String pageUrl = RELEASES_PAGE;
	public static volatile String error = "";
	public static volatile int progress = 0;
	/** Set by the Cancel button of the update dialog: the running download stops at the next chunk and is deleted. */
	private static volatile boolean cancelRequested = false;

	private static volatile String assetUrl = "", assetSha = "";
	private static volatile long assetSize = 0L;
	private static volatile String pendingToast = null;
	private static volatile Path staged = null;
	private static volatile String stagedVersion = "";
	private static boolean started = false, hookInstalled = false;
	private static int ticks = 0;

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(8)).build();

	private Updater() {}

	// ---------- where we are ----------

	public static String currentVersion() {
		return FabricLoader.getInstance().getModContainer("ray")
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(RayClient.VERSION);
	}

	/** The jar this mod was loaded from, or null when running from source (development). */
	private static Path currentJar() {
		Optional<ModContainer> mc = FabricLoader.getInstance().getModContainer("ray");
		if (mc.isEmpty() || mc.get().getOrigin().getKind() != ModOrigin.Kind.PATH) return null;
		for (Path p : mc.get().getOrigin().getPaths()) {
			if (Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".jar")) return p;
		}
		return null;
	}

	public static boolean canInstall() {
		return currentJar() != null;
	}

	// ---------- driven from the client tick ----------

	public static void tick() {
		String toast = pendingToast;
		if (toast != null) {
			pendingToast = null;
			Toasts.push(toast);
		}
		if (started) return;
		if (++ticks < 300) return; // let the game finish starting (15 seconds) before touching the network
		started = true;
		scanStaged();
		RayConfig cfg = RayConfig.get();
		if (cfg.updateMode != 0 && state != State.READY && System.currentTimeMillis() - cfg.updateLastCheck > CHECK_EVERY_MS) {
			checkNow(false);
		}
	}

	// ---------- checking ----------

	public static void checkNow() {
		checkNow(true);
	}

	private static void checkNow(boolean manual) {
		if (state == State.CHECKING || state == State.DOWNLOADING) return;
		if (state == State.READY && !manual) return;
		state = State.CHECKING;
		error = "";
		Thread t = new Thread(() -> check(manual), "Ray-Updater");
		t.setDaemon(true);
		t.start();
	}

	private static void check(boolean manual) {
		RayConfig cfg = RayConfig.get();
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases/latest"))
				.timeout(Duration.ofSeconds(10))
				.header("Accept", "application/vnd.github+json")
				.header("User-Agent", "RayClient/" + currentVersion())
				.GET().build();
			HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			cfg.updateLastCheck = System.currentTimeMillis();
			if (res.statusCode() == 404) { // no release published yet
				state = State.UP_TO_DATE;
				return;
			}
			if (res.statusCode() != 200) throw new java.io.IOException("GitHub answered " + res.statusCode());

			JsonObject rel = JsonParser.parseString(res.body()).getAsJsonObject();
			String tag = str(rel, "tag_name");
			String ver = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
			latest = ver;
			notes = str(rel, "body");
			String html = str(rel, "html_url");
			pageUrl = UpdateLogic.trustedUrl(html) ? html : RELEASES_PAGE;

			if (ver.isEmpty() || UpdateLogic.compare(ver, currentVersion()) <= 0) {
				state = State.UP_TO_DATE;
				return;
			}
			if (ver.equals(cfg.updateSkipped) && !manual) {
				state = State.UP_TO_DATE;
				return;
			}

			List<String> names = new ArrayList<>();
			List<JsonObject> assets = new ArrayList<>();
			JsonArray arr = rel.has("assets") && rel.get("assets").isJsonArray() ? rel.getAsJsonArray("assets") : new JsonArray();
			for (JsonElement e : arr) {
				JsonObject o = e.getAsJsonObject();
				names.add(str(o, "name"));
				assets.add(o);
			}
			int idx = UpdateLogic.pickAsset(names);
			if (idx < 0) throw new java.io.IOException("the release has no jar attached");
			JsonObject asset = assets.get(idx);
			assetUrl = str(asset, "browser_download_url");
			assetSize = asset.has("size") && !asset.get("size").isJsonNull() ? asset.get("size").getAsLong() : 0L;
			assetSha = UpdateLogic.shaFromNotes(notes);
			if (!UpdateLogic.trustedUrl(assetUrl)) throw new java.io.IOException("the download address is not on GitHub");

			state = State.AVAILABLE;
			pendingToast = "Ray Client " + ver + " is available";
			if (cfg.updateMode == 2 && canInstall()) download();
		} catch (Throwable t) {
			if (manual || state == State.CHECKING) {
				error = t.getMessage() == null ? t.toString() : t.getMessage();
				state = State.ERROR;
			}
			if (!manual) state = State.IDLE; // a failed background check is silent; it is tried again later
		}
	}

	// ---------- downloading ----------

	/** Starts downloading the available update (it installs when the game is closed). */
	public static void downloadNow() {
		if (state != State.AVAILABLE && state != State.ERROR) return;
		if (assetUrl.isEmpty()) {
			checkNow(true);
			return;
		}
		Thread t = new Thread(Updater::download, "Ray-Updater-Download");
		t.setDaemon(true);
		t.start();
	}

	private static void download() {
		Path jar = currentJar();
		if (jar == null) {
			error = "Updating works with the packaged mod jar, not from source";
			state = State.ERROR;
			return;
		}
		if (state == State.DOWNLOADING) return;
		cancelRequested = false;
		state = State.DOWNLOADING;
		progress = 0;
		error = "";
		String ver = latest;
		Path part = jar.getParent().resolve("ray-client-" + ver + ".jar.part");
		try {
			HttpRequest req = HttpRequest.newBuilder(URI.create(assetUrl)).timeout(Duration.ofMinutes(3))
				.header("Accept", "application/octet-stream")
				.header("User-Agent", "RayClient/" + currentVersion()).GET().build();
			HttpResponse<InputStream> res = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
			if (cancelRequested) throw new java.io.IOException("cancelled");
			if (res.statusCode() != 200) throw new java.io.IOException("download failed (" + res.statusCode() + ")");
			long total = res.headers().firstValueAsLong("Content-Length").orElse(assetSize);
			if (total > MAX_BYTES || assetSize > MAX_BYTES) throw new java.io.IOException("the file is unexpectedly large");
			long got = 0L;
			try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(part)) {
				byte[] buf = new byte[64 * 1024];
				int n;
				while ((n = in.read(buf)) > 0) {
					if (cancelRequested) throw new java.io.IOException("cancelled");
					got += n;
					if (got > MAX_BYTES) throw new java.io.IOException("the file is unexpectedly large");
					out.write(buf, 0, n);
					if (total > 0) progress = (int) Math.min(99, got * 100 / total);
				}
			}
			if (assetSize > 0 && got != assetSize) throw new java.io.IOException("the download is incomplete");
			String problem = verify(part, ver, assetSha);
			if (problem != null) throw new java.io.IOException(problem);

			staged = part;
			stagedVersion = ver;
			progress = 100;
			state = State.READY;
			pendingToast = "Ray Client " + ver + " downloaded. Restart Minecraft to apply it";
			installHook();
		} catch (Throwable t) {
			try {
				Files.deleteIfExists(part);
			} catch (Exception ignored) {
			}
			if (cancelRequested) {
				// the player pressed Cancel: back to "update available", nothing is left behind
				cancelRequested = false;
				error = "";
				progress = 0;
				state = State.AVAILABLE;
				return;
			}
			error = t.getMessage() == null ? t.toString() : t.getMessage();
			state = State.ERROR;
		}
	}

	/** Stops a running download (the update stays available and can be started again). */
	public static void cancelDownload() {
		if (state == State.DOWNLOADING) cancelRequested = true;
	}

	/** Checks a downloaded file really is Ray Client at the expected version. Returns a problem description, or null if fine. */
	private static String verify(Path file, String expectedVersion, String sha) {
		try {
			if (!sha.isEmpty()) {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				try (InputStream in = Files.newInputStream(file)) {
					byte[] buf = new byte[64 * 1024];
					int n;
					while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
				}
				StringBuilder hex = new StringBuilder();
				for (byte b : md.digest()) hex.append(String.format("%02x", b));
				if (!hex.toString().equalsIgnoreCase(sha)) return "the checksum does not match";
			}
			try (ZipFile zip = new ZipFile(file.toFile())) {
				ZipEntry e = zip.getEntry("fabric.mod.json");
				if (e == null) return "the file is not a Fabric mod";
				try (InputStream in = zip.getInputStream(e)) {
					JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
					if (!"ray".equals(str(o, "id"))) return "the file is not Ray Client";
					String v = str(o, "version");
					if (!expectedVersion.isEmpty() && UpdateLogic.compare(v, expectedVersion) != 0) return "the file is version " + v + ", expected " + expectedVersion;
					if (UpdateLogic.compare(v, currentVersion()) <= 0) return "the file is not newer than this version";
				}
			}
			return null;
		} catch (Throwable t) {
			return "could not verify the file (" + t.getMessage() + ")";
		}
	}

	/** An update downloaded in an earlier session but not installed yet (for example if the game crashed): pick it up again. */
	private static void scanStaged() {
		Path jar = currentJar();
		if (jar == null) return;
		try (var files = Files.list(jar.getParent())) {
			for (Path p : files.toList()) {
				String n = p.getFileName().toString();
				if (!n.startsWith("ray-client-") || !n.endsWith(".jar.part")) continue;
				String ver = n.substring("ray-client-".length(), n.length() - ".jar.part".length());
				if (verify(p, ver, "") == null) {
					if (staged == null || UpdateLogic.compare(ver, stagedVersion) > 0) {
						staged = p;
						stagedVersion = ver;
					}
				} else {
					Files.deleteIfExists(p);
				}
			}
		} catch (Throwable ignored) {
		}
		if (staged != null) {
			latest = stagedVersion;
			state = State.READY;
			installHook();
		}
	}

	// ---------- installing when the game closes ----------

	private static void installHook() {
		if (hookInstalled) return;
		hookInstalled = true;
		Runtime.getRuntime().addShutdownHook(new Thread(Updater::applyOnExit, "Ray-Updater-Install"));
	}

	private static void applyOnExit() {
		try {
			Path part = staged, old = currentJar();
			if (part == null || old == null || !Files.exists(part)) return;
			Path dir = old.getParent();
			Path target = dir.resolve("ray-client-" + stagedVersion + ".jar");
			Path backups = dir.resolve("ray-backup");
			if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
				// Windows keeps the running jar locked until the game has fully exited, so a small helper waits for that
				String script = "$old='" + q(old) + "'; $part='" + q(part) + "'; $final='" + q(target) + "'; $bdir='" + q(backups) + "'\r\n"
					+ "for($i=0;$i -lt 120;$i++){\r\n"
					+ "  try { New-Item -ItemType Directory -Force -Path $bdir | Out-Null; Get-ChildItem -LiteralPath $bdir -Filter *.jar | Remove-Item -Force -ErrorAction SilentlyContinue;"
					+ " Move-Item -LiteralPath $old -Destination (Join-Path $bdir (Split-Path $old -Leaf)) -Force -ErrorAction Stop; break }\r\n"
					+ "  catch { Start-Sleep -Seconds 1 }\r\n"
					+ "}\r\n"
					+ "if(-not (Test-Path -LiteralPath $old)){ Move-Item -LiteralPath $part -Destination $final -Force }\r\n"
					+ "Remove-Item -LiteralPath $MyInvocation.MyCommand.Path -Force\r\n";
				Path ps1 = Files.createTempFile("ray-update-", ".ps1");
				Files.writeString(ps1, script, StandardCharsets.UTF_8);
				new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", ps1.toString())
					.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			} else {
				Files.createDirectories(backups);
				try (var old1 = Files.list(backups)) {
					for (Path b : old1.toList()) if (b.getFileName().toString().endsWith(".jar")) Files.deleteIfExists(b);
				}
				Files.move(old, backups.resolve(old.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				try {
					Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception e) {
					// could not put the new one in place: put the old one back so the game still starts
					Files.move(backups.resolve(old.getFileName()), old, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (Throwable ignored) {
			// never block the game from closing
		}
	}

	/** A path for a single-quoted PowerShell string. */
	private static String q(Path p) {
		return p.toAbsolutePath().toString().replace("'", "''");
	}

	// ---------- actions and text for the screens ----------

	public static void skipThisVersion() {
		RayConfig.get().updateSkipped = latest;
		RayConfig.save();
		state = State.UP_TO_DATE;
	}

	public static void openReleasePage() {
		try {
			net.minecraft.util.Util.getOperatingSystem().open(URI.create(pageUrl));
		} catch (Throwable ignored) {
		}
	}

	/** True when there is something to show on the title screen. */
	public static boolean hasNews() {
		State s = state;
		return s == State.AVAILABLE || s == State.DOWNLOADING || s == State.READY;
	}

	public static String statusLine() {
		return switch (state) {
			case IDLE -> "Version " + currentVersion() + ". Not checked yet.";
			case CHECKING -> "Checking GitHub...";
			case UP_TO_DATE -> "You have the latest version (" + currentVersion() + ").";
			case AVAILABLE -> "Version " + latest + " is available (you have " + currentVersion() + ")." + (canInstall() ? "" : " Updating works with the packaged mod jar.");
			case DOWNLOADING -> "Downloading " + latest + ": " + progress + "%";
			case READY -> "Version " + latest + " is downloaded. Close Minecraft to finish updating; your old version is kept in mods/ray-backup.";
			case ERROR -> "Update problem: " + error;
		};
	}

	private static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}
}
