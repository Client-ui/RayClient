package dev.ray;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Ray Client menu: every module and feature in ONE searchable grid (filter chips on top, favourites,
 * an enable button and a settings button on each card), plus Profiles, Waypoints and Theme pages.
 */
public class RayScreen extends Screen {
	private static final String[] PAGES = {"MODS", "SETTINGS", "PROFILES", "WAYPOINTS"};
	private static final String[] CHIPS = {"ALL", "ON", "FAV", "STATS", "WORLD", "PLAYER", "PVP", "EXTRAS", "VISUAL", "GAMEPLAY"};
	private static final int CARD_H = 70, GAP = 6, HEADER_H = 34;

	private static final String[] SORTS = {"CATEGORY", "A-Z", "ENABLED", "FAVORITES"};

	private static int page = 0;
	private static String filter = "ALL";

	public static void selectPage(String name) {
		for (int i = 0; i < PAGES.length; i++) if (PAGES[i].equalsIgnoreCase(name)) page = i;
	}

	public static void selectFilter(String name) {
		filter = name.toUpperCase();
	}

	private final Screen parent;
	private final Ui ui = new Ui();
	private TextFieldWidget search;
	private String query = "";
	private float scrollCur = 0f;
	private int scrollTarget = 0, scrollMax = 0;
	private List<String> profileNames = new ArrayList<>();
	private long profilesTime = 0;
	private boolean profilesDirty = true;
	private final long openedAt = System.currentTimeMillis();
	private long resetArmedAt = 0L;
	// tooltip state
	private Features.Card hovCard = null;
	private String tipId = "";
	private long tipSince = 0L;
	// search pill bounds from the last frame (for focus handling)
	private int sx, sy, sw, sh;
	// size of the (scaled) menu area in menu units, from the last frame
	private int viewW, viewH;
	/** Menu Size while the slider is being dragged: applied when the mouse is released, so the menu does not rescale under the cursor. */
	private Integer pendingScale = null;
	private final Map<String, String> previewText = new HashMap<>();
	private final Map<String, Long> previewAt = new HashMap<>();

	public RayScreen(Screen parent) {
		super(Text.literal("Ray Client"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ui.reset();
		search = new TextFieldWidget(textRenderer, 0, 0, 90, 10, Text.literal("Search"));
		search.setDrawsBackground(false);
		search.setMaxLength(32);
		search.setPlaceholder(Text.literal("Search modules..."));
		search.setText(query);
		search.setChangedListener(s -> {
			query = s;
			scrollTarget = 0;
			scrollCur = 0f;
		});
		addSelectableChild(search);
		if (page == 0) setInitialFocus(search);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget - (int) (verticalAmount * 28)));
		return true;
	}

	private void setPage(int p) {
		ui.resetAppear();
		page = p;
		scrollTarget = 0;
		scrollCur = 0f;
	}

	private void setFilter(String f) {
		ui.resetAppear();
		filter = f;
		scrollTarget = 0;
		scrollCur = 0f;
	}

	// ---------- render ----------

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		if (client.world == null) Brand.backdrop(ctx, width, height, System.currentTimeMillis(), 1.0f);
		RayConfig cfg = RayConfig.get();
		if (pendingScale != null && !ui.left) { // the slider was let go
			cfg.menuScale = pendingScale;
			pendingScale = null;
			ui.resetAppear();
			RayConfig.save();
		}
		// "Menu Size": everything below is laid out in a smaller or larger virtual screen and scaled to fit
		float sc = cfg.menuScale / 100.0f;
		viewW = Math.round(width / sc);
		viewH = Math.round(height / sc);
		int mx = Math.round(mouseX / sc), my = Math.round(mouseY / sc);
		if (client.world != null) ctx.fill(0, 0, width, height, Theme.fade(0xFF000000, 0.32f * Math.min(1f, (System.currentTimeMillis() - openedAt) / 190f)));
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(sc, sc);
		ui.begin(ctx, textRenderer, mx, my);
		int accent = Theme.accent();
		hovCard = null;

		// window slides up and fades in; the game behind dims slightly
		long now = System.currentTimeMillis();
		float t = cfg.uiAnimations ? Math.min(1f, (now - openedAt) / 190f) : 1f;
		float open = 1f - (1f - t) * (1f - t) * (1f - t);

		int pw = Math.min(600, viewW - 12);
		int ph = Math.min(360, viewH - 12);
		int px = (viewW - pw) / 2, py = (viewH - ph) / 2 + Math.round((1f - open) * 14f);
		ui.shadow(px, py, pw, ph, 8);
		ui.panel(px, py, pw, ph, 8, ui.glass(Theme.PANEL), Theme.STROKE);

		drawHeader(px, py, pw);

		// ease the scroll position
		scrollCur += (scrollTarget - scrollCur) * 0.4f;
		if (Math.abs(scrollTarget - scrollCur) < 0.5f) scrollCur = scrollTarget;
		int off = Math.round(scrollCur);

		if (page == 0) {
			drawMods(ctx, px, py, pw, ph, off, delta);
		} else {
			if (getFocused() != null) setFocused(null);
			drawPage(px, py, pw, ph, off);
		}
		drawTooltip(now);

		ui.poll();

		// focus the search box when it is clicked, release it when something else is
		if (page == 0 && ui.leftPressed) {
			boolean inside = ui.mx >= sx && ui.mx < sx + sw && ui.my >= sy && ui.my < sy + sh;
			if (inside) setFocused(search);
			else setFocused(null);
		}
		ctx.getMatrices().popMatrix();
	}

	private void drawHeader(int px, int py, int pw) {
		// logo lock-up
		Brand.bolt(ui.ctx, px + 17, py + 7, 1, 1.0f, System.currentTimeMillis());
		Brand.word(ui.ctx, "RAY", px + 32, py + 7, 2, 0xFFFFFFFF);
		Brand.word(ui.ctx, "CLIENT", px + 32, py + 24, 1, Theme.accent());

		// page tabs (padding shrinks and the editor shortcut shortens / hides when the window is narrow)
		int x = px + 88, y = py + 9;
		boolean inGame = client.world != null;
		int avail = pw - 88 - 36;
		int pad = 18, gap = 4;
		String editorLabel = "HUD EDITOR";
		for (int attempt = 0; attempt < 4; attempt++) {
			int tabs = 0;
			for (String p : PAGES) tabs += ui.w(p) + pad + gap;
			if (tabs + ui.w(editorLabel) + pad <= avail) break;
			if (attempt == 0) editorLabel = "EDITOR";
			else if (attempt == 1) { pad = 10; gap = 2; editorLabel = "HUD EDITOR"; }
			else if (attempt == 2) editorLabel = "EDITOR";
			else editorLabel = null;
		}
		int indX = x, indW = 30;
		for (int i = 0; i < PAGES.length; i++) {
			final int idx = i;
			String p = PAGES[i];
			int w = ui.w(p) + pad;
			boolean sel = page == i;
			float hv = ui.anim("pgh" + i, ui.over(x, y, w, 18));
			float sv = ui.anim("pgs" + i, sel);
			if (hv > 0.02f && !sel) ui.rrect(x, y, w, 18, 4, Theme.fade(0xFFFFFFFF, 0.05f * hv));
			if (sel) {
				indX = x;
				indW = w;
			}
			ui.text(p, x + pad / 2, y + 5, Theme.mix(Theme.mix(Theme.MUTE, Theme.DIM, hv), 0xFFFFFFFF, sv));
			ui.click("page" + i, x, y, w, 18, () -> setPage(idx));
			x += w + gap;
		}
		// the underline glides to the selected tab
		float ix = ui.animTo("tabx", indX), iw = ui.animTo("tabw", indW);
		ui.rect(Math.round(ix) + 4, py + HEADER_H - 2, Math.max(4, Math.round(iw) - 8), 2, Theme.accent());
		// HUD editor shortcut (only inside a world)
		if (editorLabel != null) {
			int ew = ui.w(editorLabel) + pad;
			boolean ehov = inGame && ui.over(x, y, ew, 18);
			ui.rrect(x, y, ew, 18, 4, inGame ? Theme.mix(Theme.CARD, Theme.CARD_HOVER, ehov ? 1f : 0f) : 0xFF0F1116);
			ui.ring(x, y, ew, 18, 4, inGame ? Theme.mix(Theme.STROKE, Theme.accent(), ehov ? 0.8f : 0.3f) : Theme.STROKE);
			ui.text(editorLabel, x + pad / 2, y + 5, inGame ? 0xFFFFFFFF : Theme.MUTE);
			if (inGame) ui.click("editor", x, y, ew, 18, () -> client.setScreen(new HudEditorScreen()));
		}

		// close
		int cx = px + pw - 26;
		boolean chov = ui.iconButton(cx, y, 18, 18, 0xFF1C1316, 0xFFB42B35, this::close);
		ui.closeIcon(cx + 6, y + 6, chov ? 0xFFFFFFFF : 0xFFFF9AA2);

		ui.rect(px + 8, py + HEADER_H, pw - 16, 1, Theme.STROKE);
	}

	// ---------- MODS page ----------

	private void resetScroll() {
		scrollTarget = 0;
		scrollCur = 0f;
	}

	private void drawMods(DrawContext ctx, int px, int py, int pw, int ph, int off, float delta) {
		RayConfig cfg = RayConfig.get();
		int left = px + 12;
		int innerW = pw - 24;

		// counts shown on the filter chips
		int total = 0, enabled = 0, favs = 0;
		Map<String, Integer> perCategory = new HashMap<>();
		for (Features.Card c : Features.cards()) {
			total++;
			if (c.on.getAsBoolean()) enabled++;
			if (c.favorite()) favs++;
			perCategory.merge(c.category.toUpperCase(), 1, Integer::sum);
		}

		// search pill (right) and chips (left, wrapping)
		sw = Math.min(118, innerW / 3);
		sh = 16;
		sx = px + pw - 12 - sw;
		sy = py + HEADER_H + 7;
		float focus = ui.anim("searchfocus", search.isFocused());
		ui.panel(sx, sy, sw, sh, 5, Theme.BG, Theme.mix(Theme.STROKE, Theme.accent(), Math.max(focus, ui.over(sx, sy, sw, sh) ? 0.5f : 0f)));
		search.setX(sx + 7);
		search.setY(sy + 4);
		search.setWidth(sw - 14);
		search.render(ctx, ui.mx, ui.my, 0f);

		int chipAreaW = innerW - sw - 8;
		int cx = left, cy = py + HEADER_H + 8, rows = 1;
		for (String chip : CHIPS) {
			int n = chip.equals("ALL") ? total : chip.equals("ON") ? enabled : chip.equals("FAV") ? favs : perCategory.getOrDefault(chip, 0);
			String shownLabel = chip + " " + n;
			int cw = ui.w(shownLabel) + 12;
			if (cx + cw > left + chipAreaW && cx > left) {
				cx = left;
				cy += 16;
				rows++;
			}
			final String label = chip;
			cx += ui.chip(cx, cy, shownLabel, filter.equals(chip), () -> setFilter(label)) + 3;
		}

		// filter, then sort
		String q = query.trim().toLowerCase();
		List<Features.Card> shown = new ArrayList<>();
		for (Features.Card c : Features.cards()) {
			if (filter.equals("ON") && !c.on.getAsBoolean()) continue;
			if (filter.equals("FAV") && !c.favorite()) continue;
			if (!filter.equals("ALL") && !filter.equals("ON") && !filter.equals("FAV") && !c.category.equalsIgnoreCase(filter)) continue;
			if (!q.isEmpty() && !c.keywords.contains(q) && !c.desc.toLowerCase().contains(q)) continue;
			shown.add(c);
		}
		Comparator<Features.Card> byName = Comparator.comparing(c -> c.name.toLowerCase());
		switch (cfg.sortMode) {
			case 1 -> shown.sort(byName);
			case 2 -> shown.sort(Comparator.<Features.Card, Boolean>comparing(c -> !c.on.getAsBoolean()).thenComparing(byName));
			case 3 -> shown.sort(Comparator.<Features.Card, Boolean>comparing(c -> !c.favorite()).thenComparing(byName));
			default -> { }
		}

		// layout: cards in a grid, or compact rows
		boolean list = cfg.listView;
		int gy = py + HEADER_H + 8 + rows * 16 + 2;
		int gh = py + ph - 24 - gy;
		int gw = innerW - 7;
		int cols = list ? 1 : cfg.menuCols > 0 ? cfg.menuCols : Math.max(2, Math.min(5, (gw + GAP) / (118 + GAP)));
		int itemH = list ? 22 : CARD_H;
		int gap = list ? 3 : GAP;
		int cardW = (gw - (cols - 1) * GAP) / cols;
		int gridRows = (shown.size() + cols - 1) / cols;
		int contentH = gridRows * (itemH + gap);
		scrollMax = Math.max(0, contentH - gh);
		scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget));
		off = Math.max(0, Math.min(scrollMax, off));

		ui.pushClip(left - 1, gy, gw + 2, gh);
		for (int i = 0; i < shown.size(); i++) {
			int x = left + (i % cols) * (cardW + GAP);
			int y = gy + (i / cols) * (itemH + gap) - off;
			if (y + itemH < gy || y > gy + gh) continue;
			if (list) drawRow(shown.get(i), x, y, cardW, itemH);
			else drawCard(shown.get(i), x, y, cardW, itemH, i);
		}
		if (shown.isEmpty()) {
			ui.center("Nothing matches.", left + gw / 2, gy + 24, Theme.DIM);
			ui.center(filter.equals("FAV") ? "Click the heart on a module to favourite it."
					: filter.equals("ON") ? "No modules are enabled yet." : "Try a different search or filter.",
				left + gw / 2, gy + 38, Theme.MUTE);
		}
		ui.popClip();
		ui.scrollbar(px + pw - 11, gy, gh, gh, contentH, off);

		// footer: counts on the left; sort and view controls on the right
		int fy = py + ph - 19;
		String count = shown.size() + " of " + total + "   " + enabled + " on";
		String sortLabel = "SORT: " + SORTS[Math.max(0, Math.min(SORTS.length - 1, cfg.sortMode))];
		int wl = ui.w("LIST") + 12, wg = ui.w("GRID") + 12, ws = ui.w(sortLabel) + 12;
		int rx = px + pw - 12 - wl;
		ui.chip(rx, fy, "LIST", cfg.listView, () -> {
			cfg.listView = true;
			resetScroll();
		});
		rx -= wg + 3;
		ui.chip(rx, fy, "GRID", !cfg.listView, () -> {
			cfg.listView = false;
			resetScroll();
		});
		rx -= ws + 8;
		ui.chip(rx, fy, sortLabel, false, () -> {
			cfg.sortMode = (cfg.sortMode + 1) % SORTS.length;
			resetScroll();
		});
		if (left + ui.w(count) + 10 < rx) ui.text(count, left, fy + 3, Theme.MUTE);

		// tooltip bookkeeping: remember how long the mouse has rested on the same module
		long now = System.currentTimeMillis();
		if (hovCard != null) {
			if (!hovCard.id.equals(tipId)) {
				tipId = hovCard.id;
				tipSince = now;
			}
		} else {
			tipId = "";
		}
	}

	private void drawTooltip(long now) {
		boolean show = hovCard != null && now - tipSince > 380L && !ui.left;
		float a = ui.anim("tipvis", show);
		if (hovCard == null || a < 0.03f) return;
		Features.Card c = hovCard;
		int maxW = 170;
		List<String> lines = ui.wrap(c.desc, maxW);
		int tw = ui.w(c.name);
		for (String l : lines) tw = Math.max(tw, ui.w(l));
		int w = Math.max(tw, ui.w("Right-click to toggle")) + 18, h = 37 + lines.size() * 11;
		int tx = ui.mx + 12, ty = ui.my + 14;
		if (tx + w > viewW - 4) tx = ui.mx - w - 8;
		if (ty + h > viewH - 4) ty = viewH - h - 4;
		int accent = Theme.accent();
		ui.shadow(tx, ty, w, h, 4);
		ui.panel(tx, ty, w, h, 4, Theme.fade(Theme.PANEL, 0.97f * a), Theme.fade(accent, 0.7f * a));
		ui.text(c.name, tx + 9, ty + 7, Theme.fade(0xFFFFFFFF, a));
		int yy = ty + 20;
		for (String l : lines) {
			ui.text(l, tx + 9, yy, Theme.fade(Theme.DIM, a));
			yy += 11;
		}
		ui.text("Right-click to toggle", tx + 9, yy + 3, Theme.fade(Theme.MUTE, a));
	}

	/** The live value for a tile (refreshed a few times a second, only for tiles that are on screen). */
	private String previewFor(Features.Card c, long now) {
		if (client.player == null || c.preview == null) return null;
		Long at = previewAt.get(c.id);
		if (at == null || now - at > 250L) {
			previewAt.put(c.id, now);
			String v = null;
			try {
				v = c.preview.get();
			} catch (Throwable ignored) {
			}
			previewText.put(c.id, v);
		}
		return previewText.get(c.id);
	}

	private void drawCard(Features.Card c, int x, int y, int w, int h, int index) {
		// entrance: tiles fade and slide up one after another
		float ap = ui.appear("cardin" + c.id, Math.min(index, 12) * 24);
		if (ap < 0.02f) return;
		y += Math.round((1f - ap) * 12f);
		boolean hov = ui.over(x, y, w, h);
		if (hov) hovCard = c;
		boolean on = c.on.getAsBoolean();
		float hv = ui.anim("card" + c.id, hov), ov = ui.anim("cardon" + c.id, on);
		int accent = Theme.accent();
		int ly = y - Math.round(hv * 2f);
		long now = System.currentTimeMillis();

		// body: vivid accent when enabled, dark steel when not
		int onTop = Theme.mix(0xFF0E1220, accent, 0.70f), onBot = Theme.mix(0xFF0E1220, accent, 0.52f);
		int offTop = Theme.mix(0xFF22262F, 0xFF2A2F3A, hv), offBot = Theme.mix(0xFF1A1D24, 0xFF20242C, hv);
		ui.rrectGrad(x, ly, w, h, 6, Theme.fade(Theme.mix(offTop, onTop, ov), ap), Theme.fade(Theme.mix(offBot, onBot, ov), ap));
		ui.ring(x, ly, w, h, 6, Theme.fade(Theme.mix(0x26FFFFFF, Theme.mix(accent, 0xFFFFFFFF, 0.35f), Math.max(ov * 0.55f, hv * 0.8f)), ap * 0.8f));

		// top row: icon on a dark plate, then the name and a second line (the live value, or the category)
		ui.rrect(x + 6, ly + 6, 26, 26, 5, Theme.fade(0x55000000, ap));
		if (ap > 0.5f) {
			ui.ctx.getMatrices().pushMatrix();
			ui.ctx.getMatrices().translate(x + 7f, ly + 7f - hv);
			ui.ctx.getMatrices().scale(1.5f, 1.5f);
			ui.ctx.drawItem(c.icon(), 0, 0);
			ui.ctx.getMatrices().popMatrix();
		}
		int tx = x + 38, tw = w - 38 - 18;
		ui.text(ui.fit(c.name, tw), tx, ly + 8, Theme.fade(0xFFFFFFFF, ap));
		String pv = on || hov ? previewFor(c, now) : null;
		String sub = pv != null ? pv : c.category.toUpperCase();
		ui.text(ui.fit(sub, tw), tx, ly + 20, Theme.fade(pv != null ? 0xFFFFFFFF : Theme.mix(0xAAFFFFFF, 0xFFFFFFFF, hv), pv != null ? ap : ap * 0.75f));

		// favourite heart (top right)
		boolean fav = c.favorite();
		float hh = ui.anim("heart" + c.id, ui.over(x + w - 20, y + 4, 16, 14));
		ui.heart(x + w - 15, ly + 7, Theme.fade(fav ? 0xFFFF6A86 : Theme.mix(0x66FFFFFF, 0xFFFFFFFF, hh), ap));
		ui.hit("fav" + c.id, x + w - 20, y + 4, 16, 14, c::toggleFavorite, () -> c.flip.run(), null);

		// bottom row: enable / disable (wide) and options (narrow), side by side
		int by = ly + h - 21;
		int bx = x + 6, total = w - 12, gapB = 4;
		int bw1 = (total - gapB) * 11 / 20, bw2 = total - gapB - bw1;
		float e1 = ui.anim("en" + c.id, ui.over(bx, y + h - 21, bw1, 15));
		ui.rrect(bx, by, bw1, 15, 4, Theme.fade(Theme.mix(0x55000000, 0x88000000, e1), ap));
		ui.center(on ? "DISABLE" : "ENABLE", bx + bw1 / 2, by + 4, Theme.fade(Theme.mix(0xFFE8ECF8, 0xFFFFFFFF, e1), ap));
		ui.hit("en" + c.id, bx, y + h - 21, bw1, 15, () -> {
			c.flip.run();
			RayConfig.save();
		}, null, null);

		int bx2 = bx + bw1 + gapB;
		float e2 = ui.anim("gear" + c.id, ui.over(bx2, y + h - 21, bw2, 15));
		ui.rrect(bx2, by, bw2, 15, 4, Theme.fade(Theme.mix(0x55000000, 0x88000000, e2), ap));
		ui.center(bw2 >= 44 ? "OPTIONS" : "...", bx2 + bw2 / 2, by + 4, Theme.fade(Theme.mix(0xFFE8ECF8, 0xFFFFFFFF, e2), ap));
		ui.hit("gear" + c.id, bx2, y + h - 21, bw2, 15, () -> client.setScreen(c.settings.apply(this)), null, null);

		// right-click anywhere on the tile toggles it
		ui.hit("card" + c.id, x, y, w, h, null, () -> {
			c.flip.run();
			RayConfig.save();
		}, null);
	}

	/** Compact one-line version of a module for the list view. */
	private void drawRow(Features.Card c, int x, int y, int w, int h) {
		boolean hov = ui.over(x, y, w, h);
		if (hov) hovCard = c;
		boolean on = c.on.getAsBoolean();
		float hv = ui.anim("row" + c.id, hov), ov = ui.anim("rowon" + c.id, on);
		int accent = Theme.accent();
		ui.panel(x, y, w, h, 3, Theme.mix(Theme.CARD, Theme.CARD_HOVER, hv), Theme.mix(Theme.STROKE, accent, Math.min(1f, 0.55f * ov + 0.3f * hv)));
		ui.ctx.drawItem(c.icon(), x + 4, y + 3);
		ui.text(ui.fit(c.name, Math.max(40, w / 2 - 40)), x + 26, y + 7, Theme.TEXT);
		ui.text(c.category.toUpperCase(), x + w / 2 + 6, y + 7, Theme.mix(Theme.MUTE, Theme.fade(accent, 0.9f), ov));

		// right side: [switch] [gear] [heart]
		int hx = x + w - 20;
		float hh = ui.anim("rowheart" + c.id, ui.over(hx, y + 3, 16, 16));
		ui.heart(hx + 5, y + 8, c.favorite() ? 0xFFFF5C7A : Theme.mix(0xFF3A425A, Theme.DIM, hh));
		ui.hit("rfav" + c.id, hx, y + 3, 16, 16, c::toggleFavorite, () -> c.flip.run(), null);

		int gx = hx - 22;
		float gv = ui.anim("rowgear" + c.id, ui.over(gx, y + 3, 18, 16));
		ui.rrect(gx, y + 3, 18, 16, 3, Theme.mix(0xFF232A40, accent, gv));
		ui.slidersIcon(gx + 4, y + 7, Theme.mix(Theme.DIM, 0xFFFFFFFF, gv));
		ui.hit("rgear" + c.id, gx, y + 3, 18, 16, () -> client.setScreen(c.settings.apply(this)), null, null);

		int tx = gx - 32;
		float k = ui.anim("rowk" + c.id, on);
		ui.rrect(tx, y + 6, 24, 10, 3, Theme.mix(0xFF2B3248, Theme.GOOD, k));
		ui.rrect(tx + 2 + Math.round(k * 12), y + 7, 8, 8, 2, 0xFFFFFFFF);
		ui.hit("rtog" + c.id, tx - 3, y + 3, 30, 16, () -> {
			c.flip.run();
			RayConfig.save();
		}, null, null);

		ui.hit("row" + c.id, x, y, w, h, null, () -> {
			c.flip.run();
			RayConfig.save();
		}, null);
	}

	// ---------- other pages ----------

	private void drawPage(int px, int py, int pw, int ph, int off) {
		RayConfig cfg = RayConfig.get();
		int vx = px + 12, vy = py + HEADER_H + 8, vw = pw - 28, vh = ph - HEADER_H - 8 - 22;
		ui.pushClip(vx - 2, vy, vw + 4, vh);
		Ui.Form f = new Ui.Form(ui, vx, vy - off, vw);

		switch (page) {
			case 1 -> settingsPage(f, cfg);
			case 2 -> profilesPage(f);
			case 3 -> waypointsPage(f, cfg);
			default -> {}
		}

		int contentH = f.bottom() - (vy - off);
		ui.popClip();
		scrollMax = Math.max(0, contentH - vh);
		scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget));
		ui.scrollbar(px + pw - 11, vy, vh, vh, contentH, off);
		ui.right("ESC to close", px + pw - 12, py + ph - 15, Theme.MUTE);
	}

	private void profilesPage(Ui.Form f) {
		f.section("Quick presets");
		for (Presets.Preset p : Presets.ALL) {
			int[] r = f.full();
			float hv = ui.anim("pre" + p.name(), ui.over(r[0], r[1], r[2], Ui.H));
			ui.panel(r[0], r[1], r[2], Ui.H, 3, Theme.mix(Theme.CARD, Theme.CARD_HOVER, hv), Theme.mix(Theme.STROKE, Theme.accent(), hv * 0.35f));
			ui.text(p.name(), r[0] + 8, r[1] + 8, Theme.TEXT);
			int nameW = ui.w(p.name());
			ui.text(ui.fit(p.blurb(), Math.max(20, r[2] - nameW - 90)), r[0] + 8 + nameW + 10, r[1] + 8, Theme.MUTE);
			ui.labelButton(r[0] + r[2] - 56, r[1] + 4, 48, 16, "Apply", 0xFF1E3A8A, () -> Presets.apply(p));
		}
		f.note("A preset turns exactly those HUD modules on and every other HUD module off. Your module settings and positions are kept.");

		f.section("Profiles");
		ui.button(f.full(), "+ Save current setup as new profile", () -> {
			String name = Profiles.nextName();
			Profiles.save(name);
			profilesDirty = true;
			Toasts.push("Profile saved: " + name);
		});
		if (profilesDirty || System.currentTimeMillis() - profilesTime > 1500L) {
			profileNames = Profiles.list();
			profilesTime = System.currentTimeMillis();
			profilesDirty = false;
		}
		if (profileNames.isEmpty()) {
			f.note("No profiles yet. Save one to switch between layouts and settings instantly.");
		}
		for (String pn : new ArrayList<>(profileNames)) {
			int[] r = f.full();
			ui.panel(r[0], r[1], r[2], Ui.H, 3, Theme.CARD, Theme.STROKE);
			ui.text(ui.fit(pn, r[2] - 130), r[0] + 8, r[1] + 8, Theme.TEXT);
			int bx = r[0] + r[2] - 118;
			ui.labelButton(bx, r[1] + 4, 38, 16, "Load", 0xFF1E3A8A, () -> {
				Profiles.load(pn);
				Toasts.push("Profile loaded: " + pn);
			});
			ui.labelButton(bx + 42, r[1] + 4, 38, 16, "Save", 0xFF14532D, () -> {
				Profiles.save(pn);
				Toasts.push("Profile updated: " + pn);
			});
			ui.labelButton(bx + 84, r[1] + 4, 32, 16, "Del", 0xFF7F1D1D, () -> {
				Profiles.delete(pn);
				profilesDirty = true;
				Toasts.push("Profile deleted: " + pn);
			});
		}
	}

	private void waypointsPage(Ui.Form f, RayConfig cfg) {
		f.section("Waypoints");
		ui.button(f.full(), "+ Add waypoint at my position", () -> RayClient.addWaypoint(client));
		ui.toggle(f.half(), "Show in World", cfg.waypointMarkers, () -> cfg.waypointMarkers = !cfg.waypointMarkers);
		ui.toggle(f.half(), "Show Distance", cfg.waypointShowDist, () -> cfg.waypointShowDist = !cfg.waypointShowDist);
		f.note("Waypoints show in the world as markers. Enable the Minimap module (World) to see them on a map too.");
		if (cfg.waypoints.isEmpty()) {
			f.note("No waypoints yet. Press B in-game to drop one where you stand.");
		}
		for (int i = 0; i < cfg.waypoints.size(); i++) {
			final int idx = i;
			RayConfig.Waypoint w = cfg.waypoints.get(i);
			int[] r = f.full();
			ui.panel(r[0], r[1], r[2], Ui.H, 3, Theme.CARD, Theme.STROKE);
			String dimName = w.dim.contains("nether") ? "Nether" : (w.dim.contains("end") ? "End" : "Overworld");
			String label = w.name + "   " + (int) w.x + "  " + (int) w.y + "  " + (int) w.z + "   " + dimName;
			ui.text(ui.fit(label, r[2] - 40), r[0] + 8, r[1] + 8, Theme.TEXT);
			int bx = r[0] + r[2] - 22;
			boolean hov = ui.iconButton(bx, r[1] + 4, 16, 16, 0xFF3A1519, 0xFFB42B35, () -> {
				if (idx < RayConfig.get().waypoints.size()) RayConfig.get().waypoints.remove(idx);
				RayConfig.save();
			});
			ui.closeIcon(bx + 4, r[1] + 8, hov ? 0xFFFFFFFF : 0xFFFF9AA2);
		}
	}

	/** General client settings (kept out of the module list on purpose) + interface, appearance, data and About. */
	private void settingsPage(Ui.Form f, RayConfig cfg) {
		f.section("General");
		ui.toggle(f.half(), "Client HUD", cfg.enabled, () -> cfg.enabled = !cfg.enabled);
		ui.toggle(f.half(), "Title Screen", cfg.customTitle, () -> cfg.customTitle = !cfg.customTitle);
		ui.toggle(f.half(), "Loading Screen", cfg.customLoading, () -> cfg.customLoading = !cfg.customLoading);
		ui.toggle(f.half(), "Menu Background", cfg.customBackground, () -> cfg.customBackground = !cfg.customBackground);
		ui.toggle(f.half(), "Styled Buttons", cfg.styledMenuButtons, () -> cfg.styledMenuButtons = !cfg.styledMenuButtons);
		f.note("Client HUD is the master switch for every HUD module. The title screen, loading screen and menu background (singleplayer, multiplayer, options) apply the next time they show.");

		f.section("Updates");
		String[] updateModes = {"Manual", "Notify", "Auto"};
		ui.choice(f.full(), "Updates", updateModes[cfg.updateMode], () -> cfg.updateMode = (cfg.updateMode + 1) % updateModes.length);
		f.note(Updater.statusLine());
		ui.button(f.half(), "Check Now", Updater::checkNow);
		if (Updater.state == Updater.State.READY) ui.button(f.half(), "Quit Game to Update", () -> client.scheduleStop());
		else if (Updater.state == Updater.State.AVAILABLE || Updater.state == Updater.State.ERROR) ui.button(f.half(), "Download Update", Updater::downloadNow);
		else ui.button(f.half(), "Release Page", Updater::openReleasePage);
		f.note("Manual: nothing happens until you press Check Now (or Check for updates on the start menu). Notify: checks github.com/" + Updater.REPO + " about every 6 hours and tells you. Auto: also downloads the update and installs it when you close the game. Nothing about you is sent.");

		f.section("Menu Size");
		int shownScale = pendingScale != null ? pendingScale : cfg.menuScale;
		ui.sliderShown(f.full(), "Menu Size", 50, 200, shownScale, shownScale + "%", v -> pendingScale = v);
		int[] sizes = {70, 85, 100, 115, 130, 150};
		int sizeTop = f.reserve(18);
		int cxs = f.x();
		for (int sz : sizes) {
			final int val = sz;
			cxs += ui.chip(cxs, sizeTop, sz + "%", shownScale == sz, () -> {
				pendingScale = null;
				cfg.menuScale = val;
				ui.resetAppear();
			}) + 4;
		}
		f.note("Drag the slider to any size from 50% to 200%; it applies when you let go. The chips are quick picks.");

		int[] colOpts = {0, 2, 3, 4, 5, 6};
		int colTop = f.reserve(18);
		int ccx = f.x();
		ui.text("Tiles per row", ccx, colTop + 3, Theme.DIM);
		ccx += ui.w("Tiles per row") + 10;
		for (int co : colOpts) {
			final int val = co;
			ccx += ui.chip(ccx, colTop, co == 0 ? "AUTO" : String.valueOf(co), cfg.menuCols == co, () -> {
				cfg.menuCols = val;
				ui.resetAppear();
			}) + 4;
		}

		f.section("Interface");
		ui.toggle(f.half(), "Animations", cfg.uiAnimations, () -> cfg.uiAnimations = !cfg.uiAnimations);
		ui.toggle(f.half(), "Soft Shadows", cfg.uiShadows, () -> cfg.uiShadows = !cfg.uiShadows);
		ui.toggle(f.half(), "Notifications", cfg.toastsOn, () -> cfg.toastsOn = !cfg.toastsOn);
		ui.slider(f.half(), "Window Glass", 40, 100, cfg.uiGlass, "%", v -> cfg.uiGlass = v);
		f.note("Window Glass is how solid the menu windows are: lower lets the blurred game show through. How blurry it is comes from Minecraft's own Menu Background Blur setting in Video Settings.");

		f.section("Zoom");
		ui.sliderShown(f.half(), "Zoom Strength", 15, 100, cfg.zoomLevel, Features.zoomText(cfg.zoomLevel), v -> cfg.zoomLevel = v);
		ui.toggle(f.half(), "Smooth Zoom", cfg.smoothZoom, () -> cfg.smoothZoom = !cfg.smoothZoom);

		f.section("Appearance");
		int top = f.reserve(30);
		for (int i = 0; i < Theme.COLORS.length; i++) {
			final int idx = i;
			int sx2 = f.x() + 2 + i * 34;
			int sy2 = top + 2;
			boolean sel = cfg.accent == i && !cfg.rainbow;
			boolean hov = ui.over(sx2, sy2, 26, 26);
			float sv = ui.anim("sw" + i, sel || hov);
			ui.rrect(sx2, sy2, 26, 26, 4, Theme.COLORS[i]);
			if (sel) ui.border(sx2 - 2, sy2 - 2, 30, 30, 0xFFFFFFFF);
			else if (sv > 0.05f) ui.border(sx2 - 1, sy2 - 1, 28, 28, Theme.fade(0xFFFFFFFF, 0.55f * sv));
			ui.click("sw" + i, sx2, sy2, 26, 26, () -> {
				cfg.accent = idx;
				cfg.rainbow = false;
			});
		}
		ui.toggle(f.half(), "Rainbow Accent", cfg.rainbow, () -> cfg.rainbow = !cfg.rainbow);
		ui.toggle(f.half(), "Text Shadow", cfg.textShadow, () -> cfg.textShadow = !cfg.textShadow);
		ui.slider(f.half(), "HUD Background", 0, 100, cfg.hudOpacity, "%", v -> cfg.hudOpacity = v);
		f.note("The accent colour is used across this menu, the title screen and your HUD.");

		f.section("Your settings");
		ui.button(f.half(), "Back Up Settings", () -> {
			String name = RayConfig.backup();
			Toasts.push(name != null ? "Backup saved: " + name : "Backup failed");
		});
		boolean armed = System.currentTimeMillis() - resetArmedAt < 4000L;
		ui.button(f.half(), armed ? "Click again to confirm" : "Reset All Settings", () -> {
			if (armed) {
				RayConfig.resetAll();
				resetArmedAt = 0L;
				Toasts.push("All settings reset (old file kept as a backup)");
			} else {
				resetArmedAt = System.currentTimeMillis();
			}
		});
		ui.button(f.full(), "Open Config Folder", () -> Sys.openFolder(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()));
		f.note("Settings are saved safely: if the file is ever damaged it is kept next to the config as ray.json.broken-... and a backup of the last good copy is ray.json.bak.");

		f.section("About");
		f.note("Ray Client " + RayClient.VERSION + " for Minecraft " + VersionGuard.SUPPORTED + ".");
		f.note("You are running Minecraft " + VersionGuard.detected() + (VersionGuard.supported() ? ": supported." : ": NOT supported by this build."));
		f.note("Ray Client is built for exactly one Minecraft version because Minecraft changes its internals in every release. On any other version the client stops with a clear message instead of misbehaving.");
	}

	@Override
	public void close() {
		RayConfig.save();
		client.setScreen(parent);
	}
}
