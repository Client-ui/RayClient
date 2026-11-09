package dev.ray;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Keeps one misbehaving feature from taking the whole game down. A HUD part that throws is skipped for that frame; if it
 * keeps failing it is paused for ten seconds and a short notice is shown. Each distinct error is printed to the log once.
 */
public final class Safe {
	private static final class State {
		int fails;
		long mutedUntil;
	}

	private static final Map<String, State> STATES = new HashMap<>();
	private static final Map<String, Boolean> LOGGED = new HashMap<>();

	private Safe() {}

	/** Runs a HUD element's render with protection. */
	public static void render(String name, BiConsumer<DrawContext, RenderTickCounter> element, DrawContext ctx, RenderTickCounter tc) {
		State st = STATES.computeIfAbsent(name, k -> new State());
		long now = System.currentTimeMillis();
		if (now < st.mutedUntil) return;
		try {
			element.accept(ctx, tc);
			if (st.fails > 0 && now % 64L == 0L) st.fails--; // a rare hiccup is forgiven over time
		} catch (RuntimeException e) {
			report(name, e, st, now);
		}
	}

	/** Runs one piece of work (for example a single HUD module) with protection; returns false if it failed. */
	public static boolean run(String name, Runnable work) {
		State st = STATES.computeIfAbsent(name, k -> new State());
		long now = System.currentTimeMillis();
		if (now < st.mutedUntil) return false;
		try {
			work.run();
			return true;
		} catch (RuntimeException e) {
			report(name, e, st, now);
			return false;
		}
	}

	/** True while a part is paused after repeated errors. */
	public static boolean muted(String name) {
		State st = STATES.get(name);
		return st != null && System.currentTimeMillis() < st.mutedUntil;
	}

	/** Records an error caught by the caller (use with muted() in per-frame loops, where creating a lambda each time would waste memory). */
	public static void failed(String name, RuntimeException e) {
		report(name, e, STATES.computeIfAbsent(name, k -> new State()), System.currentTimeMillis());
	}

	private static void report(String name, RuntimeException e, State st, long now) {
		String key = name + ":" + e;
		if (LOGGED.put(key, Boolean.TRUE) == null) {
			System.err.println("[Ray] " + name + " failed: " + e);
			StackTraceElement[] tr = e.getStackTrace();
			for (int i = 0; i < Math.min(6, tr.length); i++) System.err.println("[Ray]    at " + tr[i]);
		}
		if (++st.fails >= 3) {
			st.fails = 0;
			st.mutedUntil = now + 10_000L;
			Toasts.push(name + " hit an error and was paused for 10 s");
		}
	}
}
