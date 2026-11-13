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
 * Ray Client's main menu: "Orbit and Dock".
 *
 *   orbit   the logo sits in two faint, slowly turning orbits with small satellites, the way the logo's own ring does
 *   dock    the actions live in a glass dock at the bottom; icons swell toward the cursor and name themselves on hover
 *
 * Nothing else competes for attention: no cards, no tips, no status panels (those live in the Mods screen). Update
 * news is a quiet line at the bottom left. It replaces the vanilla title screen (see MinecraftClientMixin) and can be
 * switched off in the Client category.
 */
public class RayTitleScreen extends Screen {
	private static final String MC_VERSION = "1.21.11";

	/** One dock entry: an item icon, or (when icon is null) a drawn glyph: 0 = sliders, 1 = close. */
	private record Entry(String label, ItemStack icon, int glyph, Runnable action) {}

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

	// ---------- orbits ----------

	/** One faint elliptical orbit drawn as dots, with a satellite that leaves a short fading trail. */
	private void orbit(DrawContext ctx, float cx, float cy, float a, float b, float tilt, float angle, float speedDir, float alpha, float time) {
		double ct = Math.cos(tilt), st = Math.sin(tilt);
		final int dots = 110;
		for (int i = 0; i < dots; i++) {
			double th = i * (Math.PI * 2 / dots);
			double ex = a * Math.cos(th), ey = b * Math.sin(th);
			int px = Math.round((float) (cx + ex * ct - ey * st)), py = Math.round((float) (cy + ex * st + ey * ct));
			float depth = 0.55f + 0.45f * (float) Math.sin(th); // the near side (bottom) is a little brighter
			ctx.fill(px, py, px + 1, py + 1, Theme.fade(0xFFFFFFFF, 0.17f * depth * alpha));
		}
		// satellite and trail
		for (int k = 0; k < 14; k++) {
			double th = angle - speedDir * k * 0.045;
			double ex = a * Math.cos(th), ey = b * Math.sin(th);
			int px = Math.round((float) (cx + ex * ct - ey * st)), py = Math.round((float) (cy + ex * st + ey * ct));
			float depth = 0.6f + 0.4f * (float) Math.sin(th);
			float fade = (1f - k / 14f);
			float s = k == 0 ? 1f : 0.5f * fade * fade;
			int c = k == 0 ? 0xFFFFFFFF : Theme.accent();
			ctx.fill(px, py, px + (k == 0 ? 2 : 1), py + (k == 0 ? 2 : 1), Theme.fade(c, Math.min(1f, s * depth * alpha)));
		}
	}

	// ---------- dock ----------

	private void dock(DrawContext ctx, int vw, int vh, Entry[] entries, int dividerBefore) {
		final int base = 28, gap = 12, divW = 16, padX = 16, pillH = 46;
		float ap = ui.appear("dock", 350);
		if (ap < 0.02f) return;
		int pillBottom = vh - 22 + Math.round((1f - ap) * 40f);
		int iconBottom = pillBottom - 9;

		// how much each icon swells: strongest under the cursor, falling off smoothly with distance
		boolean near = ui.my >= pillBottom - pillH - 70 && ui.my <= vh + 10 && RayConfig.get().uiAnimations;
		int n = entries.length;
		float[] size = new float[n];
		float flat = n * (base + gap) - gap + divW;
		float x0 = (vw - flat) / 2f;
		float cur = x0;
		int hovered = -1;
		float best = 1e9f;
		for (int i = 0; i < n; i++) {
			if (i == dividerBefore) cur += divW;
			float center = cur + base / 2f;
			float d = (ui.mx - center) / 46f;
			float target = near ? 1f + 0.62f * (float) Math.exp(-d * d) : 1f;
			size[i] = base * ui.animTo("dk" + i, target);
			if (near && Math.abs(ui.mx - center) < best) {
				best = Math.abs(ui.mx - center);
				hovered = i;
			}
			cur += base + gap;
		}
		float total = -gap + divW;
		for (int i = 0; i < n; i++) total += size[i] + gap;
		int pillW = Math.round(total) + padX * 2;
		int pillX = Math.round((vw - total) / 2f) - padX;
		ui.rrect(pillX, pillBottom - pillH, pillW, pillH, 16, Theme.fade(0xFFFFFFFF, 0.065f * ap));
		ui.ring(pillX, pillBottom - pillH, pillW, pillH, 16, Theme.fade(0xFFFFFFFF, 0.10f * ap));

		float x = pillX + padX;
		for (int i = 0; i < n; i++) {
			if (i == dividerBefore) {
				ui.rect(Math.round(x + divW / 2f - 1), iconBottom - 24, 1, 22, Theme.fade(0xFFFFFFFF, 0.14f * ap));
				x += divW;
			}
			Entry e = entries[i];
			float s = size[i];
			int top = Math.round(iconBottom - s);
			int ix = Math.round(x);
			boolean hov = i == hovered && ui.over(ix, top - 6, Math.round(s), Math.round(s) + 12 + 12);
			float k = s / 16f;
			ui.ctx.getMatrices().pushMatrix();
			ui.ctx.getMatrices().translate((float) ix, (float) top);
			if (e.icon() != null) {
				ui.ctx.getMatrices().scale(k, k);
				ui.ctx.drawItem(e.icon(), 0, 0);
			} else {
				ui.ctx.getMatrices().scale(k / 1.75f, k / 1.75f);
				ui.rrect(0, 0, 28, 28, 8, Theme.fade(0xFFFFFFFF, 0.10f));
				if (e.glyph() == 1) ui.closeIcon(10, 10, Theme.DIM);
				else ui.slidersIcon(8, 9, Theme.DIM);
			}
			ui.ctx.getMatrices().popMatrix();
			ui.hit("dock" + e.label(), ix, top - 6, Math.round(s), Math.round(s) + 14, e.action(), null, null);
			x += s + gap;
		}

		// the name of the icon under the cursor floats above it
		if (hovered >= 0) {
			Entry e = entries[hovered];
			float tagAp = ui.anim("docktag", true);
			float xx = pillX + padX;
			for (int i = 0; i < hovered; i++) xx += size[i] + gap + (i + 1 == dividerBefore ? divW : 0);
			if (hovered == dividerBefore) xx += divW;
			int cx = Math.round(xx + size[hovered] / 2f);
			int tw = ui.w(e.label()) + 18;
			int ty = Math.round(iconBottom - size[hovered]) - 24;
			ui.rrect(cx - tw / 2, ty, tw, 17, 5, Theme.fade(0xFF111116, 0.96f * ap * tagAp));
			ui.ring(cx - tw / 2, ty, tw, 17, 5, Theme.fade(0xFFFFFFFF, 0.14f * ap * tagAp));
			ui.center(e.label(), cx, ty + 5, Theme.fade(0xFFFFFFFF, ap * tagAp));
		}
	}

	// ---------- frame ----------

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
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(sc, sc);
		ui.begin(ctx, textRenderer, UiScale.mouse(mouseX, sc), UiScale.mouse(mouseY, sc));

		int m = Math.max(10, Math.min(22, vw / 24));
		float t = now / 1000f;
		float intro = Theme.easeOutCubic(Math.min(1f, (now - born) / 900f));
		float logoAp = Theme.easeOutCubic(Math.min(1f, (now - born) / 700f));

		// ---- the logo and its orbits (everything is centred on the logo) ----
		int logoH = Math.max(48, Math.min(92, vh / 3 - 8));
		int cx = vw / 2, top = Math.max(m + 16, vh / 2 - logoH - 26) + Math.round((1f - logoAp) * 10f);
		float ocx = cx + px * 6f, ocy = top + logoH / 2f + py * 3f;
		float oa = Math.min(vw * 0.30f, 168f) * (0.9f + 0.1f * intro);
		orbit(ctx, ocx, ocy, oa, oa * 0.27f, -0.28f, t * 0.55f, 1f, logoAp, t);
		orbit(ctx, ocx, ocy, oa * 1.42f, oa * 0.40f, 0.16f, -t * 0.34f + 2f, -1f, logoAp * 0.8f, t);
		Brand.logo(ctx, cx + Math.round(px * 3f), top, logoH, logoAp, now);

		// ---- name ----
		int ny = top + logoH + 16;
		String nm = "Ray Client";
		float nsc = 1.6f;
		int nw = Math.round(ui.headWidth(nm) * nsc);
		ui.scaledHead(nm, cx - nw / 2, ny, nsc, Theme.fade(0xFFFFFFFF, logoAp));
		int hour = java.time.LocalTime.now().getHour();
		String part = hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
		String user = client.getSession() != null ? client.getSession().getUsername() : "";
		if (now - statAt > 1000L) { // counting builds the module list, so do it once a second, not every frame
			statAt = now;
			int on = 0;
			for (Features.Card c : Features.cards()) if (c.on.getAsBoolean()) on++;
			modsOn = on;
		}
		ui.center(user.isEmpty() ? part : part + ", " + user, cx, ny + 21, Theme.fade(Theme.DIM, logoAp));
		ui.center("Minecraft " + MC_VERSION + "  \u00b7  " + modsOn + (modsOn == 1 ? " module on" : " modules on"), cx, ny + 34, Theme.fade(Theme.MUTE, logoAp));

		// ---- dock ----
		Entry[] entries = {
			new Entry("Singleplayer", new ItemStack(Items.GRASS_BLOCK), 0, () -> client.setScreen(new SelectWorldScreen(this))),
			new Entry("Multiplayer", new ItemStack(Items.COMPASS), 0, () -> client.setScreen(new MultiplayerScreen(this))),
			new Entry("Mods", new ItemStack(Items.NETHER_STAR), 0, () -> {
				RayScreen.selectPage("MODS");
				client.setScreen(new RayScreen(this));
			}),
			new Entry("Profiles", new ItemStack(Items.CHEST), 0, () -> {
				RayScreen.selectPage("PROFILES");
				client.setScreen(new RayScreen(this));
			}),
			new Entry("Options", null, 0, () -> client.setScreen(new OptionsScreen(this, client.options))),
			new Entry("Quit", null, 1, () -> client.scheduleStop()),
		};
		dock(ctx, vw, vh, entries, 4);

		// ---- update news: one quiet line at the top, and the check link at the bottom left ----
		Updater.State us = Updater.state;
		if (Updater.hasNews()) {
			float uAp = ui.appear("updbanner", 500);
			if (uAp > 0.02f) {
				String msg = us == Updater.State.AVAILABLE ? "Version " + Updater.latest + " is available"
					: us == Updater.State.DOWNLOADING ? "Downloading " + Updater.latest + "  " + Updater.progress + "%"
					: "Restart to finish updating to " + Updater.latest;
				String btn = us == Updater.State.AVAILABLE ? (Updater.canInstall() ? "Update" : "Open") : us == Updater.State.READY ? "Restart" : null;
				int bw = ui.w(msg) + 26 + (btn != null ? ui.w(btn) + 24 : 0), bh = 20;
				int bx = (vw - bw) / 2, by = m - 2 - Math.round((1f - uAp) * 14f);
				ui.rrect(bx, by, bw, bh, 5, Theme.fade(0xFF111116, 0.94f * uAp));
				ui.ring(bx, by, bw, bh, 5, Theme.fade(0xFFFFFFFF, 0.12f * uAp));
				ui.text(msg, bx + 12, by + 6, Theme.fade(0xFFFFFFFF, uAp));
				if (btn != null) {
					int bbw = ui.w(btn) + 14, bbx = bx + bw - bbw - 3;
					float hv = ui.anim("updbtn", ui.over(bbx, by + 3, bbw, bh - 6));
					ui.rrect(bbx, by + 3, bbw, bh - 6, 5, Theme.fade(Theme.mix(Theme.accent(), 0xFFFFFFFF, 0.12f * hv), uAp));
					ui.center(btn, bbx + bbw / 2, by + 6, Theme.fade(Ui.onAccent(), uAp));
					ui.hit("updbtn", bbx, by + 3, bbw, bh - 6, UpdateDialog::start, null, null);
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
		int ly = vh - m - 8;
		ui.text(ver, m, ly, Theme.fade(Theme.MUTE, intro));
		int lx = m + ui.w(ver) + 12;
		int lw = ui.w(label);
		boolean lhov = action != null && ui.over(lx - 2, ly - 3, lw + 4, 14);
		float lh = ui.anim("updlink", lhov);
		ui.text(label, lx, ly, Theme.fade(Theme.mix(us == Updater.State.ERROR ? 0xFFFF8A94 : Theme.MUTE, 0xFFFFFFFF, lh), intro));
		if (lh > 0.02f) ui.rect(lx, ly + 9, Math.round(lw * lh), 1, Theme.fade(0xFFFFFFFF, 0.5f * lh));
		if (action != null) ui.hit("updlink", lx - 2, ly - 3, lw + 4, 14, action, null, null);
		ui.right("Not affiliated with Mojang AB", vw - m, ly, Theme.fade(Theme.MUTE, intro * 0.8f));

		UpdateDialog.render(ui, vw, vh);
		ui.poll();
		ctx.getMatrices().popMatrix();
	}
}
