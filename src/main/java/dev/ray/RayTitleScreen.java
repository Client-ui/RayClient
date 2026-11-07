package dev.ray;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

/**
 * Ray Client's main menu.
 *
 *   top bar     logo on the left, Options and Quit as round buttons on the right
 *   hero        "Welcome back, <you>", a big glowing Singleplayer button, Multiplayer and Mods underneath
 *   status card your client at a glance (modules on, sky, lyrics), quick switches, and a rotating tip
 *
 * The lights in the background drift and lean slightly toward your cursor. Everything slides in one piece after another.
 * It replaces the vanilla title screen (see MinecraftClientMixin) and can be switched off in the Client category.
 */
public class RayTitleScreen extends Screen {
	private static final String MC_VERSION = "1.21.11";
	private static final String[] TIPS = {
		"Press right-shift in a world to open the Ray menu.",
		"Custom Sky has 10 pre-made skies. Try Synthwave.",
		"Lyric Sync shows your Spotify lyrics as floating words.",
		"Tap the heart on a module tile to add it to your favourites.",
		"Menu Size in Settings makes the whole menu bigger or smaller.",
		"Profiles save your whole setup so you can switch in one click.",
		"Drag HUD modules around with the HUD Editor.",
	};

	private final Ui ui = new Ui();
	private final long born = System.currentTimeMillis();
	private final ItemStack eye = new ItemStack(Items.ENDER_EYE);
	private final ItemStack star = new ItemStack(Items.NETHER_STAR);
	private final ItemStack grass = new ItemStack(Items.GRASS_BLOCK);
	private float parX = 0f, parY = 0f;
	// cached once per second: counting every module each frame is wasteful
	private long statAt = 0L;
	private int modulesOn = 0, modulesAll = 0;

	public RayTitleScreen() {
		super(Text.literal("Ray Client"));
	}

	@Override
	protected void init() {
		ui.reset();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	// ---------- pieces ----------

	/** The big primary button: accent gradient, glow, and a light that sweeps across it. */
	private void hero(int x, int y, int w, int h, String label, String sub, int index, Runnable action) {
		float ap = ui.appear("hero" + label, 80 + index * 90);
		if (ap < 0.02f) return;
		x -= Math.round((1f - ap) * 22f);
		boolean hov = ui.over(x, y, w, h);
		float hv = ui.anim("hero" + label, hov);
		int accent = Theme.accent();
		long now = System.currentTimeMillis();

		ui.shadow(x, y, w, h, 12);
		ui.glow(x, y, w, h, 12, accent, 0.55f + 0.45f * hv);
		ui.rrectGrad(x, y, w, h, 12, Theme.fade(Theme.mix(accent, 0xFFFFFFFF, 0.12f + 0.10f * hv), ap), Theme.fade(Theme.mix(0xFF0B0F20, accent, 0.62f + 0.1f * hv), ap));
		ui.ring(x, y, w, h, 12, Theme.fade(Theme.mix(accent, 0xFFFFFFFF, 0.45f), 0.8f * ap));

		// shine that travels across
		int band = 30;
		int span = w - 24 + band;
		int sx = x + 12 + (int) ((now / 11L) % (span + 90)) - band;
		int x1 = Math.max(x + 12, sx), x2 = Math.min(x + w - 12, sx + band);
		if (x2 > x1) ui.rect(x1, y + 3, x2 - x1, h - 6, Theme.fade(0xFFFFFFFF, 0.07f * ap));

		// icon tile
		int tile = h - 14;
		ui.rrect(x + 7, y + 7, tile, tile, 9, Theme.fade(0x66000000, ap));
		if (ap > 0.4f) {
			float bob = hv * (float) Math.sin(now / 170.0) * 1.2f;
			ui.ctx.getMatrices().pushMatrix();
			ui.ctx.getMatrices().translate(x + 7 + tile / 2f - 12f, y + 7 + tile / 2f - 12f + bob);
			ui.ctx.getMatrices().scale(1.5f, 1.5f);
			ui.ctx.drawItem(grass, 0, 0);
			ui.ctx.getMatrices().popMatrix();
		}
		int tx = x + 7 + tile + 10;
		ui.scaledText(label, tx, y + h / 2 - 11, 1.25f, Theme.fade(0xFFFFFFFF, ap), true);
		ui.text(sub, tx, y + h / 2 + 3, Theme.fade(Theme.mix(0xFFDCE4FF, 0xFFFFFFFF, hv), 0.85f * ap));
		// arrow that nudges forward on hover
		int ax = x + w - 22 + Math.round(hv * 4f);
		ui.pix(new String[]{"#..", "##.", "###", "##.", "#.."}, ax, y + h / 2 - 3, 2, Theme.fade(0xFFFFFFFF, ap));
		ui.hit("hero" + label, x, y, w, h, action, null, null);
	}

	/** A medium glass button with an icon. */
	private void glass(int x, int y, int w, int h, String label, ItemStack icon, boolean accented, int index, Runnable action) {
		float ap = ui.appear("glass" + label, 140 + index * 90);
		if (ap < 0.02f) return;
		x -= Math.round((1f - ap) * 22f);
		boolean hov = ui.over(x, y, w, h);
		float hv = ui.anim("glass" + label, hov);
		int accent = Theme.accent();
		int top = Theme.mix(accented ? Theme.mix(0xFF1A2038, accent, 0.30f) : 0xFF181D30, Theme.mix(0xFF212850, accent, 0.5f), hv);
		int bot = Theme.mix(accented ? Theme.mix(0xFF0E1226, accent, 0.14f) : 0xFF0E1224, Theme.mix(0xFF141A36, accent, 0.25f), hv);
		ui.glow(x, y, w, h, 10, accent, hv * 0.8f);
		ui.rrectGrad(x, y, w, h, 10, Theme.fade(top, ap), Theme.fade(bot, ap));
		ui.ring(x, y, w, h, 10, Theme.fade(Theme.mix(accented ? Theme.mix(0xFF2A3047, accent, 0.6f) : 0xFF2A3047, accent, hv), ap));
		if (ap > 0.4f) ui.ctx.drawItem(icon, x + 8, y + h / 2 - 8);
		ui.text(label, x + 30, y + h / 2 - 4, Theme.fade(Theme.mix(0xFFE4E9F8, 0xFFFFFFFF, hv), ap));
		ui.hit("glass" + label, x, y, w, h, action, null, null);
	}

	private void roundButton(int cx, int cy, int r, int id, boolean danger, Runnable action) {
		boolean hov = ui.over(cx - r, cy - r, r * 2, r * 2);
		float hv = ui.anim("rb" + id, hov);
		int accent = danger ? 0xFFE0384A : Theme.accent();
		ui.glow(cx - r, cy - r, r * 2, r * 2, r, accent, hv * 0.8f);
		ui.circle(cx, cy, r, Theme.mix(0xFF161B2E, accent, 0.65f * hv));
		ui.ring(cx - r, cy - r, r * 2, r * 2, r, Theme.mix(0xFF2A3047, accent, hv));
		if (danger) ui.closeIcon(cx - 3, cy - 3, Theme.mix(0xFFFF9AA2, 0xFFFFFFFF, hv));
		else ui.slidersIcon(cx - 5, cy - 4, Theme.mix(Theme.DIM, 0xFFFFFFFF, hv));
		ui.hit("rb" + id, cx - r, cy - r, r * 2, r * 2, action, null, null);
	}

	private void statRow(int x, int y, int w, String key, String value, int color) {
		ui.text(key, x, y, Theme.MUTE);
		ui.right(ui.fit(value, Math.max(20, w - ui.w(key) - 10)), x + w, y, color);
	}

	// ---------- frame ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		ui.begin(ctx, textRenderer, mouseX, mouseY);
		RayConfig cfg = RayConfig.get();

		// the background leans a little toward the cursor (eased so it never jumps)
		float tx = (mouseX / (float) Math.max(1, width) - 0.5f) * 2f, ty = (mouseY / (float) Math.max(1, height) - 0.5f) * 2f;
		parX += (tx - parX) * Math.min(1f, ui.dt * 4f);
		parY += (ty - parY) * Math.min(1f, ui.dt * 4f);
		Brand.backdrop(ctx, width, height, now, 1.0f, cfg.uiAnimations ? parX : 0f, cfg.uiAnimations ? parY : 0f);
		float intro = Math.max(0f, Math.min(1f, (now - born) / 650f));

		if (now - statAt > 1000L) {
			statAt = now;
			int on = 0, all = 0;
			for (Features.Card c : Features.cards()) {
				all++;
				if (c.on.getAsBoolean()) on++;
			}
			modulesOn = on;
			modulesAll = all;
		}

		int m = Math.max(10, Math.min(22, width / 24));
		boolean wide = width >= 380;

		// ---- top bar ----
		Brand.bolt(ctx, m + 9, m - 2, 1, intro, now);
		Brand.word(ctx, "RAY", m + 24, m - 2, 2, Theme.fade(0xFFFFFFFF, intro));
		Brand.word(ctx, "CLIENT", m + 24, m + 15, 1, Theme.fade(Theme.accent(), intro));
		roundButton(width - m - 9, m + 8, 9, 0, false, () -> client.setScreen(new OptionsScreen(this, client.options)));
		roundButton(width - m - 9 - 24, m + 8, 9, 1, true, () -> client.scheduleStop());

		// ---- update banner (only when there is news) ----
		if (Updater.hasNews()) {
			float uAp = ui.appear("updbanner", 400);
			if (uAp > 0.02f) {
				Updater.State us = Updater.state;
				String msg = us == Updater.State.AVAILABLE ? "Update " + Updater.latest + " available"
					: us == Updater.State.DOWNLOADING ? "Downloading " + Updater.latest + "  " + Updater.progress + "%"
					: "Update " + Updater.latest + " ready - installs when you close the game";
				String btn = us == Updater.State.AVAILABLE && Updater.canInstall() ? "UPDATE" : us == Updater.State.AVAILABLE ? "OPEN" : null;
				int bw2 = ui.w(msg) + 24 + (btn != null ? ui.w(btn) + 22 : 0);
				int bh2 = 18, bx2 = (width - bw2) / 2, by2 = m + 2 - Math.round((1f - uAp) * 14f);
				ui.glow(bx2, by2, bw2, bh2, 9, Theme.accent(), 0.5f * uAp);
				ui.rrectGrad(bx2, by2, bw2, bh2, 9, Theme.fade(0xFF1A2038, 0.96f * uAp), Theme.fade(0xFF0E1226, 0.96f * uAp));
				ui.ring(bx2, by2, bw2, bh2, 9, Theme.fade(Theme.accent(), 0.8f * uAp));
				ui.circle(bx2 + 9, by2 + bh2 / 2, 2, Theme.fade(Theme.accent(), uAp));
				ui.text(msg, bx2 + 16, by2 + 5, Theme.fade(0xFFFFFFFF, uAp));
				if (btn != null) {
					int bbw = ui.w(btn) + 12, bbx = bx2 + bw2 - bbw - 3;
					boolean hov = ui.over(bbx, by2 + 3, bbw, 12);
					float hv = ui.anim("updbtn", hov);
					ui.rrect(bbx, by2 + 3, bbw, 12, 6, Theme.mix(Theme.fade(Theme.accent(), 0.7f), Theme.accent(), hv));
					ui.center(btn, bbx + bbw / 2, by2 + 5, 0xFFFFFFFF);
					ui.hit("updbtn", bbx, by2 + 3, bbw, 12, Updater.canInstall() ? Updater::downloadNow : Updater::openReleasePage, null, null);
				}
			}
		}

		// ---- hero column ----
		int heroW = wide ? Math.min(236, (int) (width * 0.54f)) : width - m * 2;
		int heroX = m;
		int heroH = 38, rowH = 24;
		int blockH = 11 + 22 + 12 + heroH + 8 + rowH + 6 + rowH;
		int y = Math.max(m + 36, (height - blockH) / 2 + 6);

		String user = client.getSession() != null ? client.getSession().getUsername() : "Player";
		float gAp = Theme.easeOutCubic(Math.min(1f, (now - born) / 500f));
		ui.text("WELCOME BACK", heroX + Math.round((1f - gAp) * -16f), y, Theme.fade(Theme.accent(), gAp));
		y += 11;
		ui.scaledText(ui.fit(user, (int) (heroW / 1.9f)), heroX + Math.round((1f - gAp) * -16f), y, 1.9f, Theme.fade(0xFFFFFFFF, gAp), true);
		y += 22;
		ui.text("Minecraft " + MC_VERSION + "  -  Ray Client " + RayClient.VERSION, heroX, y, Theme.fade(Theme.DIM, gAp));
		y += 12;

		hero(heroX, y, heroW, heroH, "SINGLEPLAYER", "Jump into one of your worlds", 0, () -> client.setScreen(new SelectWorldScreen(this)));
		y += heroH + 8;
		int half = (heroW - 6) / 2;
		glass(heroX, y, half, rowH, "MULTIPLAYER", eye, false, 1, () -> client.setScreen(new MultiplayerScreen(this)));
		glass(heroX + half + 6, y, half, rowH, "MODS", star, true, 2, () -> client.setScreen(new RayScreen(this)));
		y += rowH + 6;
		glass(heroX, y, heroW, rowH, "PROFILES", new ItemStack(Items.COMPARATOR), false, 3, () -> {
			RayScreen.selectPage("PROFILES");
			client.setScreen(new RayScreen(this));
		});

		// ---- status card ----
		if (wide) {
			int cw = Math.max(150, Math.min(190, width - heroX - heroW - m * 2 - 4));
			int cx = width - m - cw;
			int cy = Math.max(m + 36, (height - blockH) / 2 + 6);
			float cAp = ui.appear("statcard", 260);
			int ch = 150;
			if (cAp > 0.02f && cx > heroX + heroW + 6) {
				int slide = Math.round((1f - cAp) * 24f);
				int px = cx + slide;
				ui.shadow(px, cy, cw, ch, 12);
				ui.rrectGrad(px, cy, cw, ch, 12, Theme.fade(0xFF171C30, 0.94f * cAp), Theme.fade(0xFF0C1020, 0.94f * cAp));
				ui.ring(px, cy, cw, ch, 12, Theme.fade(0xFF2A3047, cAp));
				ui.rrect(px + 12, cy + 1, cw - 24, 2, 1, Theme.fade(Theme.accent(), 0.7f * cAp));
				ui.text("YOUR CLIENT", px + 10, cy + 10, Theme.fade(Theme.DIM, cAp));

				int rx = px + 10, rw = cw - 20, ry = cy + 26;
				statRow(rx, ry, rw, "Modules on", modulesOn + " / " + modulesAll, Theme.fade(0xFFFFFFFF, cAp));
				ry += 12;
				statRow(rx, ry, rw, "Sky", cfg.skyOn ? Sky.presetName(cfg.skyPreset) : "Off", Theme.fade(cfg.skyOn ? Theme.accent() : Theme.MUTE, cAp));
				ry += 12;
				String lyr = !cfg.lyricsOn ? "Off" : Lyrics.spotifyFound && !Lyrics.title.isEmpty() ? Lyrics.title : "Waiting for Spotify";
				statRow(rx, ry, rw, "Lyrics", lyr, Theme.fade(cfg.lyricsOn ? Theme.accent() : Theme.MUTE, cAp));
				ry += 12;
				statRow(rx, ry, rw, "Menu size", cfg.menuScale + "%", Theme.fade(0xFFFFFFFF, cAp));
				ry += 18;

				ui.text("QUICK SWITCHES", rx, ry, Theme.fade(Theme.MUTE, cAp));
				ry += 12;
				int chx = rx;
				chx += ui.chip(chx, ry, "HUD", cfg.enabled, () -> {
					cfg.enabled = !cfg.enabled;
					RayConfig.save();
				}) + 3;
				chx += ui.chip(chx, ry, "SKY", cfg.skyOn, () -> {
					cfg.skyOn = !cfg.skyOn;
					RayConfig.save();
				}) + 3;
				ui.chip(chx, ry, "LYRICS", cfg.lyricsOn, () -> {
					cfg.lyricsOn = !cfg.lyricsOn;
					RayConfig.save();
				});
				ry += 22;

				// rotating tip
				String tip = TIPS[(int) ((now / 7000L) % TIPS.length)];
				ui.rect(rx, ry - 4, rw, 1, Theme.fade(0xFF2A3047, cAp));
				int ty2 = ry + 2;
				for (String line : ui.wrap(tip, rw)) {
					ui.text(line, rx, ty2, Theme.fade(Theme.DIM, cAp));
					ty2 += 10;
				}
			}
		}

		// ---- check for updates (bottom left) ----
		{
			Updater.State us = Updater.state;
			String label;
			Runnable action = null;
			switch (us) {
				case CHECKING -> label = "CHECKING" + ".".repeat(1 + (int) ((now / 350L) % 3));
				case AVAILABLE -> {
					label = "UPDATE TO " + Updater.latest;
					action = Updater.canInstall() ? Updater::downloadNow : Updater::openReleasePage;
				}
				case DOWNLOADING -> label = "DOWNLOADING " + Updater.progress + "%";
				case READY -> label = "READY - INSTALLS ON EXIT";
				default -> {
					label = "CHECK FOR UPDATES";
					action = Updater::checkNow;
				}
			}
			int ubw = ui.w(label) + 20, ubh = 15, ubx = 6, uby = height - 30;
			boolean uhov = action != null && ui.over(ubx, uby, ubw, ubh);
			float uhv = ui.anim("updcheck", uhov);
			int uacc = us == Updater.State.AVAILABLE || us == Updater.State.READY ? Theme.accent() : 0xFF2A3047;
			ui.glow(ubx, uby, ubw, ubh, 7, Theme.accent(), uhv * 0.7f);
			ui.rrectGrad(ubx, uby, ubw, ubh, 7, Theme.mix(0xFF181D30, Theme.accent(), 0.45f * uhv), Theme.mix(0xFF0E1224, Theme.accent(), 0.2f * uhv));
			ui.ring(ubx, uby, ubw, ubh, 7, Theme.mix(uacc, Theme.accent(), uhv));
			ui.center(label, ubx + ubw / 2, uby + 4, Theme.mix(Theme.DIM, 0xFFFFFFFF, Math.max(uhv, us == Updater.State.AVAILABLE ? 1f : 0f)));
			if (action != null) ui.hit("updcheck", ubx, uby, ubw, ubh, action, null, null);
			String note = switch (us) {
				case UP_TO_DATE -> "You are up to date";
				case ERROR -> "Could not check (" + Updater.error + ")";
				default -> null;
			};
			if (note != null) ui.text(ui.fit(note, width - ubw - 24), ubx + ubw + 8, uby + 4, us == Updater.State.ERROR ? 0xFFFF8A94 : Theme.MUTE);
		}
		ui.text("Ray Client v" + RayClient.VERSION, 6, height - 12, Theme.MUTE);
		ui.right("Not affiliated with Mojang AB", width - 6, height - 12, Theme.MUTE);

		ui.poll();
	}
}
