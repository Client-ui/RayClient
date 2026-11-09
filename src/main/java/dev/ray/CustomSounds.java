package dev.ray;

import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Your own hit sounds. Drop .wav or .ogg files into the sounds folder and pick one in the Hit Sound settings.
 *
 * MP3 / MP4 / M4A / AAC / FLAC are not supported: Java has no decoder for them built in. {@link #unsupportedCount()}
 * counts such files so the settings page can tell you to convert them (any free converter turns them into .wav).
 */
public final class CustomSounds {
	/** Decoded 16-bit little-endian PCM. */
	record Pcm(byte[] data, int channels, float rate) {}

	private static final Map<String, Pcm> CACHE = new HashMap<>();
	private static final Map<String, Long> CACHE_TIME = new HashMap<>();
	private static final ExecutorService AUDIO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ray-audio");
		t.setDaemon(true);
		return t;
	});

	private static List<String> listCache = new ArrayList<>();
	private static int unsupportedCache = 0;
	private static long listTime = 0L;

	private CustomSounds() {}

	private static Path soundsDir;

	public static Path folder() {
		if (soundsDir == null) {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("ray-sounds");
			try {
				Files.createDirectories(p);
			} catch (IOException ignored) {
				// listing will just come back empty
			}
			soundsDir = p;
		}
		return soundsDir;
	}

	private static String ext(String name) {
		int i = name.lastIndexOf('.');
		return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
	}

	private static void rescan() {
		long now = System.currentTimeMillis();
		if (now - listTime < 1000L) return;
		listTime = now;
		List<String> ok = new ArrayList<>();
		int bad = 0;
		File[] files = folder().toFile().listFiles();
		if (files != null) {
			for (File f : files) {
				if (!f.isFile()) continue;
				String e = ext(f.getName());
				if (e.equals("wav") || e.equals("ogg")) ok.add(f.getName());
				else if (e.equals("mp3") || e.equals("mp4") || e.equals("m4a") || e.equals("aac") || e.equals("flac")) bad++;
			}
		}
		ok.sort(String.CASE_INSENSITIVE_ORDER);
		listCache = ok;
		unsupportedCache = bad;
	}

	/** Playable files in the sounds folder (refreshed at most once a second). */
	public static List<String> list() {
		rescan();
		return listCache;
	}

	/** MP3 / MP4 / ... files found that cannot be played. */
	public static int unsupportedCount() {
		rescan();
		return unsupportedCache;
	}

	/** Opens the sounds folder in the system file browser (best effort). */
	public static void openFolder() {
		Path p = folder();
		AUDIO.execute(() -> {
			try {
				if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(p.toFile());
			} catch (Throwable ignored) {
				// the path is shown on the settings page as a fallback
			}
		});
	}

	/**
	 * Plays a file from the sounds folder. volume 0..1, pitch 1.0 = original speed.
	 * Returns false if the file does not exist (so the caller can fall back to the normal sound); decoding and playback
	 * happen on a background thread and fail silently.
	 */
	public static boolean play(String name, float volume, float pitch) {
		if (name == null || name.isEmpty()) return false;
		String e = ext(name);
		if (!e.equals("wav") && !e.equals("ogg")) return false; // cannot decode it: let the caller use the normal sound
		Path file = folder().resolve(name).normalize();
		if (!file.startsWith(folder()) || !Files.isRegularFile(file)) return false;
		AUDIO.execute(() -> {
			try {
				Pcm pcm = load(name, file);
				if (pcm == null) return;
				AudioFormat fmt = new AudioFormat(pcm.rate() * Math.max(0.25f, pitch), 16, pcm.channels(), true, false);
				Clip clip = AudioSystem.getClip();
				clip.addLineListener(ev -> {
					if (ev.getType() == LineEvent.Type.STOP) clip.close();
				});
				clip.open(fmt, pcm.data(), 0, pcm.data().length);
				if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
					FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
					float db = (float) (20.0 * Math.log10(Math.max(0.0001, Math.min(1.0, volume))));
					gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
				}
				clip.start();
			} catch (Throwable ignored) {
				// no audio device / unreadable file: stay silent rather than crash the game
			}
		});
		return true;
	}

	private static Pcm load(String name, Path file) throws Exception {
		long modified = Files.getLastModifiedTime(file).toMillis();
		Pcm cached = CACHE.get(name);
		if (cached != null && CACHE_TIME.getOrDefault(name, 0L) == modified) return cached;
		Pcm pcm = ext(name).equals("ogg") ? decodeOgg(file) : decodeWav(file);
		if (pcm != null) {
			CACHE.put(name, pcm);
			CACHE_TIME.put(name, modified);
		}
		return pcm;
	}

	static Pcm decodeWav(Path file) throws Exception {
		try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
			AudioFormat base = in.getFormat();
			AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16,
				base.getChannels(), base.getChannels() * 2, base.getSampleRate(), false);
			try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, in)) {
				return new Pcm(pcm.readAllBytes(), base.getChannels(), base.getSampleRate());
			}
		}
	}

	static Pcm decodeOgg(Path file) throws IOException {
		byte[] raw = Files.readAllBytes(file);
		ByteBuffer mem = MemoryUtil.memAlloc(raw.length);
		IntBuffer channels = MemoryUtil.memAllocInt(1);
		IntBuffer rate = MemoryUtil.memAllocInt(1);
		ShortBuffer samples = null;
		try {
			mem.put(raw).flip();
			samples = STBVorbis.stb_vorbis_decode_memory(mem, channels, rate);
			if (samples == null) return null;
			ByteBuffer out = ByteBuffer.allocate(samples.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN);
			out.asShortBuffer().put(samples);
			return new Pcm(out.array(), channels.get(0), rate.get(0));
		} finally {
			if (samples != null) MemoryUtil.memFree(samples);
			MemoryUtil.memFree(mem);
			MemoryUtil.memFree(channels);
			MemoryUtil.memFree(rate);
		}
	}
}
