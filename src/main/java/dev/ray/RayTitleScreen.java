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

	/** The primary button: a lit accent surface with an icon plate, a big label and an arrow that nudges on hover. */
	private void hero(int x, int y, int w, int h, String label, String sub, int index, Runnable action) {
		float ap = ui.appear("hero" + label, 80 + index * 90);
		if (ap < 0.02f) return;
		x -= Math.round((1f - ap) * 18f);
		boolean hov = ui.over(x, y, w, h);
		float hv = ui.anim("hero" + label, hov);
		float pr = ui.anim("herop" + label, ui.held(x, y, w, h));
		y += Math.round(pr);
		int accent = Theme.accent();
		long now = System.currentTimeMillis();

		ui.glow(x, y, w, h, 10, accent, (0.45f + 0.55f * hv) * ap);
		ui.shadow(x, y, w, h, 10);
		int top = Theme.mix(Theme.mix(accent, 0xFFFFFFFF, 0.10f), Theme.mix(accent, 0xFFFFFFFF, 0.22f), hv);
		int bot = Theme.mix(Theme.mix(accent, 0xFF000000, 0.42f), Theme.mix(accent, 0xFF000000, 0.26f), hv);
		ui.rrectGrad(x, y, w, h, 10, Theme.fade(top, ap), Theme.fade(bot, ap));
		ui.ring(x, y, w, h, 10, Theme.fade(Theme.mix(accent, 0xFFFFFFFF, 0.45f), 0.7f * ap));
		ui.rect(x + 10, y + 1, w - 20, 1, Theme.fade(0xFFFFFFFF, 0.30f * ap));

		int tile = h - 14;
		ui.rrect(x + 7, y + 7, tile, tile, 8, Theme.fade(0x40000000, ap));
		ui.ring(x + 7, y + 7, tile, tile, 8, Theme.fade(0x33FFFFFF, ap));
		if (ap > 0.4f) {
			float bob = hv * (float) Math.sin(now / 200.0) * 1.0f;
			ui.ctx.getMatrices().pushMatrix();
			ui.ctx.getMatrices().translate(x + 7 + tile / 2f - 12f, y + 7 + tile / 2f - 12f + bob);
			ui.ctx.getMatrices().scale(1.5f, 1.5f);
			ui.ctx.drawItem(grass, 0, 0);
			ui.ctx.getMatrices().popMatrix();
		}
		int tx = x + 7 + tile + 11;
		ui.scaledText(label, tx, y + h / 2 - 11, 1.25f, Theme.fade(0xFFFFFFFF, ap), true);
		ui.text(sub, tx, y + h / 2 + 3, Theme.fade(0xFFEAF0FF, 0.82f * ap));
		int ax = x + w - 22 + Math.round(hv * 4f);
		ui.pix(new String[]{"#..", "##.", "###", "##.", "#.."}, ax, y + h / 2 - 5, 2, Theme.fade(0xFFFFFFFF, ap));
		ui.hit("hero" + label, x, y - Math.round(pr), w, h, action, null, null);
	}

	/** A secondary button: glass surface with an icon plate; the plate and border take the accent on hover. */
	private void glass(int x, int y, int w, int h, String label, ItemStack icon, boolean accented, int index, Runnable action) {
		float ap = ui.appear("glass" + label, 140 + index * 90);
		if (ap < 0.02f) return;
		x -= Math.round((1f - ap) * 18f);
		boolean hov = ui.over(x, y, w, h);
		float hv = ui.anim("glass" + label, hov);
		int accent = Theme.accent();
		if (hv > 0.02f) ui.glow(x, y, w, h, 8, accent, hv * 0.6f);
		ui.rrectGrad(x, y, w, h, 8, Theme.fade(Theme.mix(0xFF1B1E25, 0xFF252A33, hv), ap), Theme.fade(Theme.mix(0xFF13161B, 0xFF1B1F26, hv), ap));
		ui.ring(x, y, w, h, 8, Theme.fade(Theme.mix(Theme.STROKE, accent, Math.max(hv * 0.85f, accented ? 0.35f : 0f)), ap));
		ui.plate(x + 4, y + 4, h - 8, accent, Math.max(hv, accented ? 0.5f : 0.1f));
		if (ap > 0.4f) ui.ctx.drawItem(icon, x + 4 + (h - 8 - 16) / 2, y + 4 + (h - 8 - 16) / 2);
		ui.spaced(label, x + h + 3, y + h / 2 - 4, Theme.fade(Theme.mix(0xFFD5DAE6, 0xFFFFFFFF, hv), ap), 1);
		ui.chevron(x + w - 12 + Math.round(hv * 2f), y + h / 2 - 2, Theme.fade(Theme.mix(Theme.MUTE, 0xFFFFFFFF, hv), ap));
		ui.hit("glass" + label, x, y, w, h, action, null, null);
	}

	/** Round icon button. kind: 0 = settings gear, 1 = close (danger), 2 = GitHub / link. */
	private void roundButton(int cx, int cy, int r, int id, int kind, Runnable action) {
		boolean hov = ui.over(cx - r, cy - r, r * 2, r * 2);
		float hv = ui.anim("rb" + id, hov);
		boolean danger = kind == 1;
		int accent = danger ? 0xFFE0384A : Theme.accent();
		if (hv > 0.02f) ui.glow(cx - r, cy - r, r * 2, r * 2, r, accent, hv * 0.6f);
		ui.circle(cx, cy, r, Theme.mix(0xFF181B22, Theme.mix(0xFF181B22, accent, 0.40f), hv));
		ui.ring(cx - r, cy - r, r * 2, r * 2, r, Theme.mix(Theme.STROKE, accent, hv));
		int glyph = Theme.mix(danger ? 0xFFFF9AA2 : Theme.DIM, 0xFFFFFFFF, hv);
		if (kind == 1) ui.closeIcon(cx - 3, cy - 3, glyph);
		else if (kind == 0) ui.slidersIcon(cx - 5, cy - 4, glyph);
		else {
			ui.pix(new String[]{"..#", ".#.", "#..", ".#.", "..#"}, cx - 6, cy - 5, 2, glyph);
			ui.pix(new String[]{"#..", ".#.", "..#", ".#.", "#.."}, cx + 1, cy - 5, 2, glyph);
		}
		ui.hit("rb" + id, cx - r, cy - r, r * 2, r * 2, action, null, null);
	}

	/** One line of the status card with an on/off dot; returns the y for the next line. */
	private int statRow(int x, int y, int w, String key, String value, boolean good, float alpha) {
		ui.circle(x + 2, y + 4, 2, Theme.fade(good ? Theme.GOOD : 0xFF4A5268, alpha));
		ui.text(key, x + 9, y, Theme.fade(Theme.MUTE, alpha));
		ui.right(ui.fit(value, Math.max(20, w - ui.w(key) - 18)), x + w, y, Theme.fade(good ? 0xFFFFFFFF : Theme.DIM, alpha));
		return y + 13;
	}

	// ---------- frame ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		RayConfig cfg = RayConfig.get();

		// the background fills the real window and leans a little toward the cursor (eased so it never jumps)
		float tx = (mouseX / (float) Math.max(1, width) - 0.5f) * 2f, ty = (mouseY / (float) Math.max(1, height) - 0.5f) * 2f;
		parX += (tx - parX) * 0.12f;
		parY += (ty - parY) * 0.12f;
		Brand.backdrop(ctx, width, height, now, 1.0f, cfg.uiAnimations ? parX : 0f, cfg.uiAnimations ? parY : 0f);

		// everything else is laid out in a virtual screen scaled to fit the window, so the menu looks right at any GUI Scale
		float sc = UiScale.fit(width, height, 560, 315, cfg.menuScale);
		int vw = UiScale.view(width, sc), vh = UiScale.view(height, sc);
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(sc, sc);
		ui.begin(ctx, textRenderer, UiScale.mouse(mouseX, sc), UiScale.mouse(mouseY, sc));
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

		int m = Math.max(10, Math.min(22, vw / 24));
		boolean wide = vw >= 380;

		// ---- top bar ----
		roundButton(vw - m - 9, m + 8, 9, 1, 1, () -> client.scheduleStop());
		roundButton(vw - m - 9 - 24, m + 8, 9, 0, 0, () -> client.setScreen(new OptionsScreen(this, client.options)));
		roundButton(vw - m - 9 - 48, m + 8, 9, 2, 2, () -> {
			try {
				net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create("https://github.com/" + Updater.REPO));
			} catch (Throwable ignored) {
			}
		});

		// ---- update banner (only when there is news) ----
		if (Updater.hasNews()) {
			float uAp = ui.appear("updbanner", 400);
			if (uAp > 0.02f) {
				Updater.State us = Updater.state;
				String msg = us == Updater.State.AVAILABLE ? "Update " + Updater.latest + " available"
					: us == Updater.State.DOWNLOADING ? "Downloading " + Updater.latest + "  " + Updater.progress + "%"
					: "Restart to finish updating to " + Updater.latest;
				String btn = us == Updater.State.AVAILABLE ? (Updater.canInstall() ? "UPDATE" : "OPEN") : us == Updater.State.READY ? "RESTART" : null;
				int bw2 = ui.w(msg) + 24 + (btn != null ? ui.w(btn) + 22 : 0);
				int bh2 = 18, bx2 = (vw - bw2) / 2, by2 = m + 2 - Math.round((1f - uAp) * 14f);
				ui.panel(bx2, by2, bw2, bh2, 6, Theme.fade(0xFF111318, 0.96f * uAp), Theme.fade(Theme.accent(), 0.7f * uAp));
				ui.circle(bx2 + 9, by2 + bh2 / 2, 2, Theme.fade(Theme.accent(), uAp));
				ui.text(msg, bx2 + 16, by2 + 5, Theme.fade(0xFFFFFFFF, uAp));
				if (btn != null) {
					int bbw = ui.w(btn) + 12, bbx = bx2 + bw2 - bbw - 3;
					boolean hov = ui.over(bbx, by2 + 3, bbw, 12);
					float hv = ui.anim("updbtn", hov);
					ui.rrect(bbx, by2 + 3, bbw, 12, 6, Theme.mix(Theme.fade(Theme.accent(), 0.7f), Theme.accent(), hv));
					ui.center(btn, bbx + bbw / 2, by2 + 5, 0xFFFFFFFF);
					ui.hit("updbtn", bbx, by2 + 3, bbw, 12, UpdateDialog::start, null, null);
				}
			}
		}

		// ---- hero column ----
		// the hero column and the status card are centred together as one composition
		final int cardW = 196, colGap = 28;
		int heroW = wide ? Math.min(250, vw - cardW - colGap - m * 2) : vw - m * 2;
		int heroX = wide ? (vw - (heroW + colGap + cardW)) / 2 : m;
		int heroH = 38, rowH = 24;
		String tipText = TIPS[(int) ((now / 7000L) % TIPS.length)];
		java.util.List<String> tipLines = ui.wrap(tipText, cardW - 22);
		int cardNeed = 150 + tipLines.size() * 10;
		int blockH = Math.max(46 + 10 + 12 + heroH + 8 + rowH + 6 + rowH, wide ? cardNeed : 0);
		int y = Math.max(m + 26, (vh - blockH) / 2 - 2);
		int blockTop = y;

		String user = client.getSession() != null ? client.getSession().getUsername() : "Player";
		float gAp = Theme.easeOutCubic(Math.min(1f, (now - born) / 500f));
		int slide = Math.round((1f - gAp) * -16f);
		// big wordmark: bolt, RAY, then CLIENT and the version
		int bw0 = Brand.boltWidth(2);
		Brand.bolt(ctx, heroX + bw0 / 2 + slide, y + 1, 2, gAp, now);
		int wx = heroX + bw0 + 10 + slide;
		Brand.word(ctx, "RAY", wx, y, 4, Theme.fade(0xFFFFFFFF, gAp));
		ui.spaced("CLIENT", wx + 2, y + 34, Theme.fade(Theme.accent(), gAp), 4);
		int vchipX = wx + 2 + ui.spacedWidth("CLIENT", 4) + 8;
		ui.rrect(vchipX, y + 32, ui.w("v" + RayClient.VERSION) + 10, 12, 6, Theme.fade(0xFF1B1F26, gAp));
		ui.ring(vchipX, y + 32, ui.w("v" + RayClient.VERSION) + 10, 12, 6, Theme.fade(Theme.STROKE_HI, gAp));
		ui.text("v" + RayClient.VERSION, vchipX + 5, y + 35, Theme.fade(Theme.DIM, gAp));
		y += 46;
		ui.text("Welcome back, " + ui.fit(user, heroW - 90) + "  -  Minecraft " + MC_VERSION, heroX + slide, y, Theme.fade(Theme.DIM, gAp));
		y += 22;

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


		// ---- status card (same vh as the button column, so the two line up) ----
		if (wide) {
			int cw = cardW;
			int cx = heroX + heroW + colGap;
			int cy = blockTop;
			int ch = blockH;
			float cAp = ui.appear("statcard", 260);
			if (cAp > 0.02f && cx > heroX + heroW + 6) {
				int px = cx + Math.round((1f - cAp) * 24f);
				ui.surface(px, cy, cw, ch, 12);
				int rx = px + 11, rw = cw - 22, ry = cy + 11;
				ui.text("YOUR CLIENT", rx, ry, Theme.fade(Theme.DIM, cAp));
				ry += 12;
				ui.rect(rx, ry, rw, 1, Theme.fade(0xFF2A3047, cAp));
				ry += 6;

				ry = statRow(rx, ry, rw, "Modules", modulesOn + " / " + modulesAll, modulesOn > 0, cAp);
				ry = statRow(rx, ry, rw, "Sky", cfg.skyOn ? Sky.presetName(cfg.skyPreset) : "Off", cfg.skyOn, cAp);
				String lyr = !cfg.lyricsOn ? "Off" : Lyrics.spotifyFound && !Lyrics.title.isEmpty() ? Lyrics.title : "Waiting for Spotify";
				ry = statRow(rx, ry, rw, "Lyrics", lyr, cfg.lyricsOn && Lyrics.spotifyFound, cAp);
				String upd = switch (Updater.state) {
					case AVAILABLE, DOWNLOADING -> "v" + Updater.latest + " available";
					case READY -> "Installs on exit";
					case CHECKING -> "Checking...";
					case UP_TO_DATE -> "Up to date";
					default -> "v" + RayClient.VERSION;
				};
				ry = statRow(rx, ry, rw, "Version", upd, Updater.state == Updater.State.UP_TO_DATE || Updater.state == Updater.State.READY || Updater.hasNews(), cAp);
				ry += 3;
				ui.rect(rx, ry, rw, 1, Theme.fade(0xFF2A3047, cAp));
				ry += 7;

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

				// the tip sits at the bottom of the card
				int tipH = tipLines.size() * 10;
				int tipY = cy + ch - 10 - tipH;
				ui.rect(rx, tipY - 9, rw, 1, Theme.fade(0xFF2A3047, cAp));
				ui.text("TIP", rx, tipY - 6, Theme.fade(Theme.accent(), 0.9f * cAp));
				for (String line : tipLines) {
					ui.text(line, rx, tipY + 5, Theme.fade(Theme.DIM, cAp));
					tipY += 10;
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
					action = UpdateDialog::start;
				}
				case DOWNLOADING -> {
					label = "DOWNLOADING " + Updater.progress + "%";
					action = UpdateDialog::start;
				}
				case READY -> {
					label = "RESTART TO UPDATE";
					action = UpdateDialog::start;
				}
				default -> {
					label = "CHECK FOR UPDATES";
					action = Updater::checkNow;
				}
			}
			int ubw = ui.w(label) + 20, ubh = 15, ubx = 6, uby = vh - 30;
			boolean uhov = action != null && ui.over(ubx, uby, ubw, ubh);
			float uhv = ui.anim("updcheck", uhov);
			int uacc = us == Updater.State.AVAILABLE || us == Updater.State.READY ? Theme.accent() : 0xFF2A3047;
			ui.rrect(ubx, uby, ubw, ubh, 4, Theme.mix(0xFF14171D, Theme.mix(0xFF14171D, Theme.accent(), 0.3f), uhv));
			ui.ring(ubx, uby, ubw, ubh, 4, Theme.mix(uacc, Theme.accent(), uhv));
			ui.center(label, ubx + ubw / 2, uby + 4, Theme.mix(Theme.DIM, 0xFFFFFFFF, Math.max(uhv, us == Updater.State.AVAILABLE ? 1f : 0f)));
			if (action != null) ui.hit("updcheck", ubx, uby, ubw, ubh, action, null, null);
			String note = switch (us) {
				case UP_TO_DATE -> "You are up to date";
				case ERROR -> "Could not check (" + Updater.error + ")";
				default -> null;
			};
			if (note != null) ui.text(ui.fit(note, vw - ubw - 24), ubx + ubw + 8, uby + 4, us == Updater.State.ERROR ? 0xFFFF8A94 : Theme.MUTE);
		}
		ui.right("Not affiliated with Mojang AB", vw - 6, vh - 12, Theme.MUTE);

		UpdateDialog.render(ui, vw, vh);
		ui.poll();
		ctx.getMatrices().popMatrix();
	}
}
