package dev.ray;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The Nametag module: puts the Ray logo in front of the nametag of every player who uses the client.
 *
 * Minecraft servers do not tell other players what software you run, so the client needs a shared "presence" service:
 * clients check in with it now and then, and look up the players around them. The service is a few lines of code (see
 * the server folder) and its address goes in ray.json ("nametagServer"). While that is empty, nothing is ever sent
 * anywhere and only your own nametag shows the logo. What is sent is only a player's public id, and only to that server.
 *
 * The logo is a character in its own font (assets/ray/font/logo.json), so it needs no rendering code: it is simply put
 * in front of the player's display name (see EntityDisplayNameMixin).
 */
public final class Nametags {
	private Nametags() {}

	public static final String GLYPH = "\uE000";
	private static final Identifier LOGO_FONT = Identifier.of("ray", "logo");

	private record Entry(boolean yes, long until) {}

	private static final Map<UUID, Entry> CACHE = new ConcurrentHashMap<>();
	private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
	private static final long YES_MS = 5 * 60_000L, NO_MS = 60_000L, BEAT_MS = 60_000L;
	private static volatile UUID self;
	private static volatile long selfSeen;
	private static volatile long lastBeat;
	private static volatile String status = "";
	private static ScheduledExecutorService worker;
	private static Text logoText;

	/** The presence server address (a system property wins over the config file), without a trailing slash. */
	public static String server() {
		String p = System.getProperty("ray.presence");
		String s = p != null && !p.isBlank() ? p : RayConfig.get().nametagServer;
		if (s == null) return "";
		s = s.trim();
		while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
		return s.startsWith("http://") || s.startsWith("https://") ? s : "";
	}

	/** One line for the settings page. */
	public static String statusLine() {
		if (server().isEmpty()) return "No presence server set: only your own nametag shows the logo. Nothing is sent anywhere.";
		return status.isEmpty() ? "Presence server: " + server() : status;
	}

	/** Called every frame the HUD draws (cheap): tells the module who the local player is. */
	public static void frame(UUID me) {
		self = me;
		selfSeen = System.currentTimeMillis();
	}

	/** Whether this player should get the logo. Safe to call for every nametag every frame. */
	public static boolean uses(UUID id) {
		RayConfig cfg = RayConfig.get();
		if (!cfg.nametagOn || id == null) return false;
		UUID me = self;
		if (me != null && me.equals(id)) return cfg.nametagSelf;
		if (server().isEmpty()) return false;
		long now = System.currentTimeMillis();
		Entry e = CACHE.get(id);
		if (e == null || now > e.until) {
			if (PENDING.size() < 200) PENDING.add(id);
			start();
		}
		return e != null && e.yes; // an expired answer is still shown while the new one is fetched, so it never flickers
	}

	/** The name with the logo in front of it. */
	public static Text decorate(Text name) {
		if (name == null) return null;
		try {
			if (name.getString().startsWith(GLYPH)) return name; // already done
			Text logo = logo();
			return logo == null ? name : logo.copy().append(name);
		} catch (Throwable t) {
			return name;
		}
	}

	private static Text logo() {
		if (logoText != null) return logoText;
		for (int shape = 0; shape < 3 && logoText == null; shape++) logoText = ClientFont.parse(GLYPH + " ", LOGO_FONT, shape);
		return logoText;
	}

	// ---------- talking to the presence server (background thread) ----------

	private static synchronized void start() {
		if (worker != null) return;
		worker = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "ray-presence");
			t.setDaemon(true);
			return t;
		});
		worker.scheduleWithFixedDelay(Nametags::tick, 1, 2, TimeUnit.SECONDS);
	}

	private static void tick() {
		try {
			String base = server();
			if (base.isEmpty() || !RayConfig.get().nametagOn) return;
			long now = System.currentTimeMillis();
			UUID me = self;
			// stay "present" while playing
			if (me != null && now - selfSeen < 5000L && now - lastBeat > BEAT_MS) {
				lastBeat = now;
				if (get(base + "/ping?u=" + compact(me), "POST") != null) status = "Presence server connected.";
			}
			// look up the players that have no fresh answer
			if (PENDING.isEmpty()) return;
			List<UUID> batch = new ArrayList<>();
			for (UUID u : PENDING) {
				if (batch.size() >= 40) break;
				batch.add(u);
			}
			PENDING.removeAll(batch);
			StringBuilder q = new StringBuilder();
			for (UUID u : batch) q.append(q.length() == 0 ? "" : ",").append(compact(u));
			String body = get(base + "/check?u=" + q, "GET");
			if (body == null) {
				status = "Presence server not reachable.";
				for (UUID u : batch) CACHE.put(u, new Entry(false, now + NO_MS));
				return;
			}
			Set<String> present = new java.util.HashSet<>(java.util.Arrays.asList(body.trim().split("[,\\s]+")));
			int found = 0;
			for (UUID u : batch) {
				boolean yes = present.contains(compact(u));
				if (yes) found++;
				CACHE.put(u, new Entry(yes, now + (yes ? YES_MS : NO_MS)));
			}
			status = "Presence server connected. " + found + " of " + batch.size() + " nearby players use Ray Client.";
		} catch (Throwable t) {
			status = "Presence server error.";
		}
	}

	private static String compact(UUID u) {
		return u.toString().replace("-", "");
	}

	/** One small request; returns the body, or null on any failure. */
	private static String get(String url, String method) {
		try {
			HttpURLConnection c = (HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
			c.setRequestMethod(method);
			c.setConnectTimeout(3000);
			c.setReadTimeout(4000);
			c.setRequestProperty("User-Agent", "RayClient");
			if (c.getResponseCode() / 100 != 2) return null;
			try (InputStream in = c.getInputStream()) {
				return new String(in.readNBytes(8192), StandardCharsets.UTF_8);
			}
		} catch (Throwable t) {
			return null;
		}
	}
}
