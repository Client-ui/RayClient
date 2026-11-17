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
 * Ray Client's main menu, built around one menu column on the left:
 *
 *   column   the greeting and a big "Ray Client" wordmark, then a glass panel with the two main actions
 *            (Singleplayer, Multiplayer) and a row of four icon tiles (Mods, Profiles, Options, Quit)
 *   right    slow orange silk ribbons that start at the panel's edge and fan out, shifting a little with the mouse,
 *            with a small status card (Minecraft version, modules on) at the bottom right
 *
 * It replaces the vanilla title screen (see MinecraftClientMixin) and can be switched off in the Client category.
 */
public class RayTitleScreen extends Screen {
	private static final String MC_VERSION = VersionGuard.detected();

	private final Ui ui = new Ui();
	private final long born = System.currentTimeMillis();
	private float parX = 0f, parY = 0f;
	private long statAt = 0L;
	private int modsOn = 0;

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

	/** A smooth elliptical glow: vertical gradient slabs whose strength follows a bell curve (no visible steps). */
	private void glow(DrawContext ctx, int cx, int cy, int rx, int ry, int color, float alpha) {
		int rgb = color & 0xFFFFFF;
		for (int x = cx - rx; x < cx + rx; x += 5) {
			float dx = (x + 2.5f - cx) / rx;
			float k = (float) Math.exp(-dx * dx * 3.2f);
			int hh = Math.round(ry * (float) Math.sqrt(Math.max(0f, 1f - dx * dx)));
			int c = Theme.fade(0xFF000000 | rgb, alpha * k);
			ctx.fillGradient(x, cy - hh, x + 5, cy, rgb, c);
			ctx.fillGradient(x, cy, x + 5, cy + hh, c, rgb);
		}
	}

	/**
	 * Silk ribbons like the launcher's: they gather on the left, fan out to the right, and the middle ones are thick and
	 * bright. Each is a sine path drawn as short dashes (about 1100 fills in all).
	 */
	private void ribbons(DrawContext ctx, int x0, int w, int h, float t, float shift, float alpha) {
		final int count = 12;
		for (int r = 0; r < count; r++) {
			float f = r / (float) (count - 1); // 0..1 across the bundle
			float centre = Math.max(0f, 1f - Math.abs(f - 0.5f) * 1.9f); // the middle ribbons are the brightest
			int col = Theme.mix(Theme.accent(), 0xFFFFD08A, 0.10f + 0.65f * centre);
			for (int x = x0; x < w; x += 4) {
				float xf = (x - x0) / (float) (w - x0);
				float env = (float) Math.sin(Math.PI * Math.min(1f, xf * 1.05f));
				float spread = (0.04f + 0.24f * xf) * h; // narrow on the left, wide on the right
				float swell = 16f * (float) Math.sin(x * 0.011f - t * 0.22f) + 0.10f * h * (float) Math.sin(x * 0.006f + t * 0.15f);
				float y = h * 0.46f + (f - 0.5f) * 2f * spread + swell
					+ (0.05f + 0.10f * xf) * h * (float) Math.sin(x * 0.019f + r * 0.35f + t * 0.40f + shift * 0.02f);
				float a = (0.14f + 0.62f * centre) * env * alpha;
				int thick = centre > 0.55f ? 2 : 1;
				ctx.fill(x, Math.round(y), x + 4, Math.round(y) + thick, Theme.fade(col, a));
			}
		}
	}

	/** One small icon tile in the row under the two main buttons. */
	private void tile(int x, int y, int w, int h, String title, ItemStack icon, int index, Runnable action) {
		float ap = ui.appear("tile" + title, 380 + index * 80);
		if (ap < 0.02f) return;
		y += Math.round((1f - ap) * 10f);
		boolean hov = ui.over(x, y, w, h);
		float hv = ui.anim("tt" + title, hov);
		ui.rrect(x, y, w, h, 8, Theme.fade(Theme.mix(Theme.CARD, Theme.CARD_HOVER, hv), ap));
		ui.ring(x, y, w, h, 8, Theme.fade(Theme.mix(Theme.LINE, Theme.accent(), hv), ap));
		if (ap > 0.5f) ui.ctx.drawItem(icon, x + (w - 16) / 2, y + 6 - Math.round(hv * 2f));
		ui.center(ui.fit(title, w - 4), x + w / 2, y + h - 14, Theme.fade(Theme.mix(Theme.MUTE, 0xFFFFFFFF, hv), ap));
		ui.hit("ttile" + title, x, y, w, h, action, null, null);
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		long now = System.currentTimeMillis();
		RayConfig cfg = RayConfig.get();

		float tx = (mouseX / (float) Math.max(1, width) - 0.5f) * 2f, ty = (mouseY / (float) Math.max(1, height) - 0.5f) * 2f;
		parX += (tx - parX) * 0.08f;
		parY += (ty - parY) * 0.08f;
		float px = cfg.uiAnimations ? parX : 0f, py = cfg.uiAnimations ? parY : 0f;
		Brand.backdrop(ctx, width, height, now, 1.0f, px, py);

		float sc = UiScale.fit(width, height, 560, 315, cfg.menuScale);
		ClientFont.scale = sc;
		int vw = UiScale.view(width, sc), vh = UiScale.view(height, sc);
		Gfx.push(ctx);
		Gfx.scale(ctx, sc, sc);
		ui.begin(ctx, textRenderer, UiScale.mouse(mouseX, sc), UiScale.mouse(mouseY, sc));

		int m = Math.max(16, Math.min(30, vw / 20));
		float t = now / 1000f;
		float intro = Theme.easeOutCubic(Math.min(1f, (now - born) / 900f));
		float heroAp = Theme.easeOutCubic(Math.min(1f, (now - born) / 700f));

		// menu column geometry
		int pw = Math.max(190, Math.min(250, Math.round(vw * 0.42f)));
		int colX = m + 2;
		int heroH = 54, panelH = 34 + 6 + 34 + 8 + 46;
		int top = Math.max(m + 36, (vh - (heroH + 22 + panelH)) / 2 + 6);

		// the silk, behind everything: it starts at the panel's edge
		int silkX = colX + pw + 14;
		glow(ctx, Math.round((silkX + vw) / 2f + px * 10f), Math.round(vh * 0.46f), Math.round((vw - silkX) * 0.62f), Math.round(vh * 0.42f), Theme.accent(), 0.30f * intro);
		ribbons(ctx, silkX, vw, vh, t, px * 40f, intro);

		// brand, top left
		int lh = 20;
		Logo.draw(ctx, m, m - 2, lh, heroAp);
		ui.trackedHead("RAY CLIENT", m + Logo.width(lh) + 9, m + 4, Theme.fade(0xFFFFFFFF, heroAp), 2);

		// hero: greeting, wordmark, one quiet line
		int hour = java.time.LocalTime.now().getHour();
		String part = hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
		String user = client.getSession() != null ? client.getSession().getUsername() : "";
		ui.text(user.isEmpty() ? part : part + ", " + user, colX, top, Theme.fade(Theme.DIM, heroAp));
		int slide = Math.round((1f - heroAp) * 16f);
		ui.scaledHead("Ray Client", colX - slide, top + 16, 3.0f, Theme.fade(0xFFFFFFFF, heroAp));

		if (now - statAt > 1000L) { // counting builds the module list, so do it once a second, not every frame
			statAt = now;
			int on = 0;
			for (Features.Card c : Features.cards()) if (c.on.getAsBoolean()) on++;
			modsOn = on;
		}
		ui.text("Minecraft " + MC_VERSION + "  -  Fabric", colX, top + 16 + 38, Theme.fade(Theme.MUTE, heroAp));

		// the glass panel with the menu
		float pAp = ui.appear("panel", 200);
		int py0 = top + heroH + 22 + Math.round((1f - pAp) * 12f);
		if (pAp > 0.02f) {
			ui.rrect(colX - 10, py0 - 10, pw + 20, panelH + 20, 12, Theme.fade(Theme.CARD, 0.60f * pAp));
			ui.ring(colX - 10, py0 - 10, pw + 20, panelH + 20, 12, Theme.fade(Theme.LINE, pAp));
			ui.primary(colX, py0, pw, 34, "Singleplayer", () -> client.setScreen(new SelectWorldScreen(this)));
			ui.pill(colX, py0 + 40, pw, 34, "Multiplayer", Theme.accent(), true, () -> client.setScreen(new MultiplayerScreen(this)));
		}
		int ty0 = py0 + 82;
		int tw = (pw - 3 * 6) / 4;
		tile(colX, ty0, tw, 46, "Mods", new ItemStack(Items.NETHER_STAR), 0, () -> {
			RayScreen.selectPage("MODS");
			client.setScreen(new RayScreen(this));
		});
		tile(colX + (tw + 6), ty0, tw, 46, "Profiles", new ItemStack(Items.CHEST), 1, () -> {
			RayScreen.selectPage("PROFILES");
			client.setScreen(new RayScreen(this));
		});
		tile(colX + 2 * (tw + 6), ty0, tw, 46, "Options", new ItemStack(Items.COMPARATOR), 2, () -> client.setScreen(new OptionsScreen(this, client.options)));
		tile(colX + 3 * (tw + 6), ty0, tw, 46, "Quit", new ItemStack(Items.BARRIER), 3, () -> client.scheduleStop());

		// status card, bottom right
		float sAp = ui.appear("status", 600);
		if (sAp > 0.02f) {
			String l1 = "Minecraft " + MC_VERSION, l2 = modsOn + (modsOn == 1 ? " module on" : " modules on");
			int sw2 = Math.max(ui.w(l1), ui.w(l2)) + 36, sh2 = 38;
			int sx = vw - m - sw2, sy = vh - 30 - sh2 + Math.round((1f - sAp) * 10f);
			ui.rrect(sx, sy, sw2, sh2, 9, Theme.fade(Theme.CARD, 0.85f * sAp));
			ui.ring(sx, sy, sw2, sh2, 9, Theme.fade(Theme.LINE, sAp));
			ui.rrect(sx + 12, sy + 15, 8, 8, 4, Theme.fade(Theme.accent(), sAp));
			ui.text(l1, sx + 28, sy + 8, Theme.fade(0xFFFFFFFF, sAp));
			ui.text(l2, sx + 28, sy + 21, Theme.fade(Theme.MUTE, sAp));
		}

		// update news: one quiet line at the top, and the check link at the bottom left
		Updater.State us = Updater.state;
		if (Updater.hasNews()) {
			float uAp = ui.appear("updbanner", 500);
			if (uAp > 0.02f) {
				String msg = us == Updater.State.AVAILABLE ? "Version " + Updater.latest + " is available"
					: us == Updater.State.DOWNLOADING ? "Downloading " + Updater.latest + "  " + Updater.progress + "%"
					: "Restart to finish updating to " + Updater.latest;
				String btn = us == Updater.State.AVAILABLE ? (Updater.canInstall() ? "Update" : "Open") : us == Updater.State.READY ? "Restart" : null;
				int bw = ui.w(msg) + 26 + (btn != null ? ui.w(btn) + 24 : 0), bh = 22;
				int bx = vw - m - bw, uy = m - 4 - Math.round((1f - uAp) * 14f);
				ui.rrect(bx, uy, bw, bh, 7, Theme.fade(Theme.CARD, uAp));
				ui.ring(bx, uy, bw, bh, 7, Theme.fade(Theme.accent(), 0.7f * uAp));
				ui.text(msg, bx + 12, uy + 7, Theme.fade(0xFFFFFFFF, uAp));
				if (btn != null) {
					int bbw = ui.w(btn) + 14, bbx = bx + bw - bbw - 3;
					float hv = ui.anim("updbtn", ui.over(bbx, uy + 3, bbw, bh - 6));
					ui.rrect(bbx, uy + 3, bbw, bh - 6, 6, Theme.fade(Theme.mix(Theme.accent(), 0xFFFFFFFF, 0.12f * hv), uAp));
					ui.center(btn, bbx + bbw / 2, uy + 7, Theme.fade(Ui.onAccent(), uAp));
					ui.hit("updbtn", bbx, uy + 3, bbw, bh - 6, UpdateDialog::start, null, null);
				}
			}
		}

		String label;
		Runnable action = null;
		switch (us) {
			case CHECKING -> label = "Checking for updates" + ".".repeat(1 + (int) ((now / 350L) % 3));
			case AVAILABLE -> {
				label = "Update to " + Updater.latest;
				action = UpdateDialog::start;
			}
			case DOWNLOADING -> {
				label = "Downloading " + Updater.progress + "%";
				action = UpdateDialog::start;
			}
			case READY -> {
				label = "Restart to update";
				action = UpdateDialog::start;
			}
			case ERROR -> {
				label = "Update check failed. Try again";
				action = Updater::checkNow;
			}
			default -> {
				label = "Check for updates";
				action = Updater::checkNow;
			}
		}
		String ver = "v" + RayClient.VERSION;
		int ly = vh - 18;
		ui.text(ver, m, ly, Theme.fade(Theme.MUTE, intro));
		int lx = m + ui.w(ver) + 12;
		int lw = ui.w(label);
		boolean lhov = action != null && ui.over(lx - 2, ly - 3, lw + 4, 14);
		float lh2 = ui.anim("updlink", lhov);
		ui.text(label, lx, ly, Theme.fade(Theme.mix(us == Updater.State.ERROR ? 0xFFFF8A94 : Theme.MUTE, 0xFFFFFFFF, lh2), intro));
		if (lh2 > 0.02f) ui.rect(lx, ly + 9, Math.round(lw * lh2), 1, Theme.fade(0xFFFFFFFF, 0.5f * lh2));
		if (action != null) ui.hit("updlink", lx - 2, ly - 3, lw + 4, 14, action, null, null);
		ui.right("Not affiliated with Mojang AB", vw - m, ly, Theme.fade(Theme.MUTE, intro * 0.8f));

		UpdateDialog.render(ui, vw, vh);
		ui.poll();
		Gfx.pop(ctx);
	}
}
