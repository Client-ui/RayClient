package dev.ray;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The update pop-up. It is drawn on top of whichever Ray screen is open (title screen, menu, settings) and blocks the
 * screen under it while it is shown.
 *
 *   1. Pressing UPDATE starts the download and shows a progress bar with a Cancel button.
 *   2. When the new version is downloaded the pop-up says Minecraft has to restart to apply the changes, with a Cancel
 *      button (keeps the update, it is installed the next time the game closes) and a green button that closes Minecraft
 *      right away (the installer then swaps the jar as the game exits).
 *   3. If something goes wrong it says what, with Close and Retry.
 *
 * ESC acts like Cancel.
 */
public final class UpdateDialog {
	private static final int GREEN = 0xFF4ADE9A;
	private static final String[] ARROW_DOWN = {"...##...", "...##...", "...##...", "...##...", "########", ".######.", "..####..", "...##..."};
	private static final String[] CHECK = {"........#", ".......##", "#.....##.", "##...##..", ".##.##...", "..###....", "...#....."};
	private static final String[] WARN = {"...##...", "...##...", "...##...", "...##...", "...##...", "........", "...##...", "...##..."};

	private static boolean open = false, closing = false, escWas = true;
	private static long openedAt = 0L, lastKick = 0L;
	private static String notesFor = "";
	private static List<String> notesLines = new ArrayList<>();

	private UpdateDialog() {}

	public static boolean isOpen() {
		return open;
	}

	/**
	 * Called by every UPDATE button. Starts the download when needed and opens the pop-up. If this install cannot update
	 * itself (running from source), the release page is opened instead.
	 */
	public static void start() {
		if (open) return;
		if (!Updater.canInstall()) {
			Updater.openReleasePage();
			return;
		}
		Updater.State s = Updater.state;
		if (s == Updater.State.AVAILABLE || s == Updater.State.ERROR) Updater.downloadNow();
		else if (s != Updater.State.DOWNLOADING && s != Updater.State.READY && s != Updater.State.CHECKING) return;
		open = true;
		closing = false;
		escWas = true; // ignore an ESC that is already down
		openedAt = System.currentTimeMillis();
		lastKick = openedAt;
	}

	public static void close() {
		open = false;
		closing = false;
	}

	// ---------- actions ----------

	private static void cancel() {
		Updater.State s = Updater.state;
		if (s == Updater.State.DOWNLOADING) {
			Updater.cancelDownload();
		} else if (s == Updater.State.READY) {
			Toasts.push("Update ready. It installs when you close Minecraft");
		}
		close();
	}

	private static void closeMinecraft() {
		closing = true;
		MinecraftClient.getInstance().scheduleStop();
	}

	private static void retry() {
		Updater.State s = Updater.state;
		if (s == Updater.State.ERROR) Updater.downloadNow();
		lastKick = System.currentTimeMillis();
	}

	// ---------- drawing ----------

	/** A short, cleaned-up excerpt of the release notes (no checksum lines, no markdown marks). */
	private static List<String> changelog() {
		String n = Updater.notes == null ? "" : Updater.notes;
		if (n.equals(notesFor)) return notesLines;
		notesFor = n;
		List<String> out = new ArrayList<>();
		for (String raw : n.split("\\r?\\n")) {
			String l = raw.replaceAll("^[#*\\-\\s>]+", "").replace("**", "").trim();
			if (l.isEmpty()) continue;
			String low = l.toLowerCase();
			if (low.contains("sha256") || low.startsWith("full changelog") || low.startsWith("ray client ") && low.contains("for minecraft")) continue;
			out.add(l);
			if (out.size() >= 3) break;
		}
		notesLines = out;
		return out;
	}

	/** Draws the pop-up over a screen that is vw x vh layout units big. Call just before {@link Ui#poll()}. */
	public static void render(Ui ui, int vw, int vh) {
		if (!open) return;
		long now = System.currentTimeMillis();
		Updater.State st = Updater.state;

		// the download may have been started from somewhere else, or finished: follow it. Anything else closes the pop-up.
		boolean ready = st == Updater.State.READY;
		boolean error = st == Updater.State.ERROR;
		boolean busy = st == Updater.State.DOWNLOADING || st == Updater.State.AVAILABLE || st == Updater.State.CHECKING;
		if (!ready && !error && !busy) {
			close();
			return;
		}
		if (st == Updater.State.AVAILABLE && now - lastKick > 1200L) {
			lastKick = now;
			Updater.downloadNow(); // a check that finished after the click: start the download now
		}

		// ESC = Cancel
		boolean esc = GLFW.glfwGetKey(ui.mc.getWindow().getHandle(), GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
		boolean escPressed = esc && !escWas;
		escWas = esc;

		float t = RayConfig.get().uiAnimations ? Theme.clamp01((now - openedAt) / 260.0f) : 1f;
		float pop = Theme.easeOutBack(t);
		float fade = Theme.easeOutCubic(t);

		ui.modal(true);
		ui.rect(0, 0, vw, vh, Theme.fade(0xFF000000, 0.62f * fade));
		ui.hit("upd-scrim", 0, 0, vw, vh, () -> { }, null, null); // swallow clicks outside the card

		int accent = Theme.accent();
		List<String> notes = ready ? changelog() : new ArrayList<>();
		int pw = Math.min(310, vw - 16);
		int ph = ready ? 168 + notes.size() * 11 + (notes.isEmpty() ? 0 : 14) : error ? 150 : 138;
		int px = (vw - pw) / 2, py = (vh - ph) / 2;

		ui.ctx.getMatrices().pushMatrix();
		float s = 0.90f + 0.10f * pop;
		ui.ctx.getMatrices().translate(vw / 2.0f, vh / 2.0f);
		ui.ctx.getMatrices().scale(s, s);
		ui.ctx.getMatrices().translate(-vw / 2.0f, -vh / 2.0f);

		ui.shadow(px, py, pw, ph, 14);
		ui.glassPanel(px, py, pw, ph, 14, Theme.fade(0xFF26262F, 0.97f * fade), Theme.fade(0xFF0C0C11, 0.97f * fade), 0.36f * fade, 0.09f * fade);

		// icon badge
		int cx = px + 30, cy = py + 32;
		int tone = ready ? GREEN : error ? Theme.BAD : accent;
		float pulse = 0.5f + 0.5f * (float) Math.sin(now / 380.0);
		ui.circle(cx, cy, 19, Theme.fade(tone, (0.10f + 0.07f * pulse) * fade));
		ui.circle(cx, cy, 15, Theme.fade(Theme.mix(0xFF101015, tone, 0.22f), fade));
		ui.ring(cx - 15, cy - 15, 30, 30, 15, Theme.fade(tone, 0.8f * fade));
		if (ready) {
			float ck = Theme.easeOutBack(Theme.clamp01((now - openedAt) / 500.0f));
			int sz = Math.max(1, Math.round(2 * ck));
			ui.pix(CHECK, cx - 9 * sz / 2, cy - 7 * sz / 2, sz, Theme.fade(0xFFFFFFFF, fade));
		} else if (error) {
			ui.pix(WARN, cx - 8, cy - 8, 2, Theme.fade(0xFFFFFFFF, fade));
		} else {
			int bob = Math.round((float) Math.sin(now / 260.0) * 1.5f);
			ui.pix(ARROW_DOWN, cx - 8, cy - 8 + bob, 2, Theme.fade(0xFFFFFFFF, fade));
		}

		// title + subtitle
		int tx = px + 58, tw = pw - 58 - 16;
		String title = closing ? "Closing Minecraft..." : ready ? "Restart required" : error ? "Update failed" : st == Updater.State.CHECKING ? "Checking for updates" : "Updating Ray Client";
		ui.scaledHead(title, tx, py + 17, 1.2f, Theme.fade(0xFFFFFFFF, fade));
		String sub;
		if (closing) sub = "The update installs as the game exits. Start Minecraft again afterwards.";
		else if (ready) sub = "Ray Client " + Updater.latest + " is installed. Minecraft needs to restart to apply the changes.";
		else if (error) sub = Updater.error.isEmpty() ? "Something went wrong." : Updater.error;
		else sub = "Downloading version " + Updater.latest + (Updater.latest.isEmpty() ? "" : "...");
		int sy = py + 34;
		List<String> lines = ui.wrap(sub, tw);
		for (int i = 0; i < Math.min(3, lines.size()); i++) {
			ui.text(lines.get(i), tx, sy + i * 11, Theme.fade(error ? 0xFFFF9AA2 : Theme.DIM, fade));
		}
		int bodyY = py + 34 + Math.min(3, lines.size()) * 11 + 8;

		if (ready) {
			// version change pill
			String change = "v" + Updater.currentVersion() + "  >  v" + Updater.latest;
			int cw = ui.w(change) + 18;
			ui.rrect(px + 16, bodyY, cw, 16, 8, Theme.fade(Theme.fade(GREEN, 0.16f), fade));
			ui.ring(px + 16, bodyY, cw, 16, 8, Theme.fade(GREEN, 0.55f * fade));
			ui.text(change, px + 25, bodyY + 4, Theme.fade(0xFFFFFFFF, fade));
			bodyY += 22;
			if (!notes.isEmpty()) {
				ui.text("WHAT'S NEW", px + 16, bodyY, Theme.fade(Theme.MUTE, fade));
				bodyY += 11;
				for (String n : notes) {
					ui.circle(px + 19, bodyY + 4, 1, Theme.fade(GREEN, fade));
					ui.text(ui.fit(n, pw - 48), px + 26, bodyY, Theme.fade(Theme.DIM, fade));
					bodyY += 11;
				}
				bodyY += 3;
			}
		} else if (!error && !closing) {
			float frac = st == Updater.State.DOWNLOADING ? Updater.progress / 100.0f : 0f;
			float shown = ui.animTo("upd-progress", frac);
			ui.progress(px + 16, bodyY + 2, pw - 32, 8, shown, accent);
			String pct = st == Updater.State.DOWNLOADING ? Updater.progress + "%" : "Starting...";
			ui.right(pct, px + pw - 16, bodyY + 15, Theme.fade(Theme.DIM, fade));
			ui.text("Please keep Minecraft open", px + 16, bodyY + 15, Theme.fade(Theme.MUTE, fade));
		}

		// buttons
		int by = py + ph - 16 - 24;
		int gap = 8, bw = (pw - 32 - gap) / 2;
		if (closing) {
			// nothing to press: the game is on its way out
		} else if (ready) {
			ui.text("Cancel keeps the update: it installs next time you close the game.", px + 16, by - 14, Theme.fade(Theme.MUTE, fade));
			ui.pill(px + 16, by, bw, 24, "Cancel", 0xFFB8B8C8, true, UpdateDialog::cancel);
			primary(ui, px + 16 + bw + gap, by, bw, 24, "Close Minecraft", GREEN, fade, UpdateDialog::closeMinecraft);
		} else if (error) {
			ui.pill(px + 16, by, bw, 24, "Close", 0xFFB8B8C8, true, UpdateDialog::close);
			primary(ui, px + 16 + bw + gap, by, bw, 24, "Retry", accent, fade, UpdateDialog::retry);
		} else {
			ui.pill(px + 16, by, pw - 32, 24, "Cancel", Theme.BAD, true, UpdateDialog::cancel);
		}

		ui.ctx.getMatrices().popMatrix();
		ui.modal(false);

		if (escPressed && !closing) cancel();
	}

	/** The main button of a pop-up: a solid colour with a soft glow. */
	private static void primary(Ui ui, int x, int y, int w, int h, String label, int color, float fade, Runnable action) {
		boolean hov = ui.over(x, y, w, h);
		float a = ui.anim("upd-pri" + label, hov);
		float p = ui.anim("upd-prip" + label, ui.held(x, y, w, h));
		int yy = y + Math.round(p);
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 420.0);
		ui.glow(x, yy, w, h, 10, color, (0.55f + 0.45f * pulse) * fade);
		ui.rrectGrad(x, yy, w, h, 10, Theme.fade(Theme.mix(color, 0xFFFFFFFF, 0.14f + 0.12f * a), fade), Theme.fade(Theme.mix(color, 0xFF000000, 0.16f - 0.08f * a), fade));
		ui.rimGlass(x, yy, w, h, 10, 0.5f * fade, 0.12f * fade);
		ui.center(label, x + w / 2, yy + (h - 8) / 2, Theme.fade(0xFF101014, fade));
		ui.click("upd-pri" + label, x, y, w, h, action);
	}
}
