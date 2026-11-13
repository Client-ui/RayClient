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
import java.util.Set;

/**
 * The Ray Client menu.
 *
 *   MODS       profiles on the left, a grid of module tiles on the right (green border = on, red = off),
 *              filter chips, a grid/list switch and a search box
 *   SETTINGS   general, updates, menu size, interface, zoom, appearance, data, about
 *   PROFILES   quick presets and saved profiles
 *   WAYPOINTS  your saved waypoints
 *
 * The whole menu is laid out for a fixed design size and scaled to fit the window (see {@link UiScale}), so it looks
 * right at any window size and GUI Scale.
 */
public class RayScreen extends Screen {
	/** MODS -> Mods, PVP -> PvP, HUD stays HUD: the ids stay upper-case, what people read does not. */
	static String nice(String id) {
		if (id.equals("HUD")) return "HUD";
		if (id.equals("PVP")) return "PvP";
		if (id.equals("A-Z")) return "A to Z";
		return id.charAt(0) + id.substring(1).toLowerCase();
	}

	private static final String[] PAGES = {"MODS", "SETTINGS", "PROFILES", "WAYPOINTS"};
	private static final String[] CHIPS = {"ALL", "NEW", "HUD", "PVP", "VISUAL", "MECHANIC", "EXTRAS", "ON", "FAV"};
	private static final String[] SORTS = {"CATEGORY", "A-Z", "ENABLED", "FAVORITES"};
	/** Modules that get a red NEW tag on their tile and the NEW filter. */
	private static final Set<String> NEW_IDS = Set.of("f_crosshair", "f_sky", "f_lyrics", "f_hitbox", "f_sideshield", "minimap");

	private static final int DESIGN_W = 760, DESIGN_H = 450;
	private static final int HEADER_H = 44, SIDE_W = 160, TILE_H = 94, GAP = 10;

	private static int page = 0;
	private static String filter = "ALL";
	/** The profile that was last loaded or saved in this session (highlighted in the sidebar). */
	private static String activeProfile = null;
	private static boolean defaultTried = false;

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
	private float sideCur = 0f;
	private int sideTarget = 0, sideMax = 0;
	private List<String> profileNames = new ArrayList<>();
	private long profilesTime = 0;
	private boolean profilesDirty = true;
	private final long openedAt = System.currentTimeMillis();
	private long resetArmedAt = 0L;
	// tooltip state
	private Features.Card hovCard = null;
	private String tipId = "";
	private long tipSince = 0L;
	// bounds from the last frame (menu units): search pill, sidebar
	private int sx, sy, sw, sh;
	private int sideX, sideY, sideW, sideH;
	private int viewW, viewH;
	private float lastSc = 1f;
	/** Menu Size while the slider is being dragged: applied when the mouse is released, so the menu does not rescale under the cursor. */
	private Integer pendingScale = null;
	private final Map<String, String> previewText = new HashMap<>();
	private final Map<String, Long> previewAt = new HashMap<>();
	private final Map<String, Long> flashAt = new HashMap<>();
	private List<Features.Card> shownCache = new ArrayList<>();
	private String shownKey = "";
	private long shownAt = 0L;
	private int totalCount = 0, enabledCount = 0;

	public RayScreen(Screen parent) {
		super(Text.literal("Ray Client"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ui.reset();
		if (!defaultTried) {
			// the first profile is a safe copy of your current setup, so you can always get back to it
			defaultTried = true;
			if (Profiles.list().isEmpty() && Profiles.save("Default")) activeProfile = "Default";
		}
		search = new TextFieldWidget(textRenderer, 0, 0, 90, 10, Text.literal("Search"));
		search.setDrawsBackground(false);
		search.setMaxLength(32);
		search.setPlaceholder(Text.literal("search..."));
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
	public boolean shouldCloseOnEsc() {
		return !UpdateDialog.isOpen();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (UpdateDialog.isOpen()) return true;
		int mxv = UiScale.mouse((int) mouseX, lastSc), myv = UiScale.mouse((int) mouseY, lastSc);
		if (page == 0 && mxv >= sideX && mxv < sideX + sideW && myv >= sideY && myv < sideY + sideH) {
			sideTarget = Math.max(0, Math.min(sideMax, sideTarget - (int) (verticalAmount * 22)));
		} else {
			scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget - (int) (verticalAmount * 34)));
		}
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

	private void resetScroll() {
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
		long now = System.currentTimeMillis();
		float t = cfg.uiAnimations ? Math.min(1f, (now - openedAt) / 260f) : 1f;
		float open = Theme.easeOutCubic(t);
		if (client.world != null) ctx.fill(0, 0, width, height, Theme.fade(0xFF000000, 0.44f * open));

		// the menu is laid out in a virtual screen and scaled so it always fits the window
		float sc = UiScale.fit(width, height, DESIGN_W, DESIGN_H, cfg.menuScale);
		ClientFont.scale = sc;
		lastSc = sc;
		viewW = UiScale.view(width, sc);
		viewH = UiScale.view(height, sc);
		int mx = UiScale.mouse(mouseX, sc), my = UiScale.mouse(mouseY, sc);
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(sc, sc);
		ui.begin(ctx, textRenderer, mx, my);
		hovCard = null;

		int pw = Math.min(DESIGN_W, viewW - 8);
		int ph = Math.min(DESIGN_H, viewH - 8);
		int px = (viewW - pw) / 2, py = (viewH - ph) / 2 + Math.round((1f - open) * 18f);

		scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-ui.dt * 16f));
		if (Math.abs(scrollTarget - scrollCur) < 0.5f) scrollCur = scrollTarget;
		sideCur += (sideTarget - sideCur) * (1f - (float) Math.exp(-ui.dt * 16f));
		if (Math.abs(sideTarget - sideCur) < 0.5f) sideCur = sideTarget;
		int off = Math.round(scrollCur);

		// floating header bar, then the body below it
		ui.surface(px, py, pw, HEADER_H, 12);
		drawHeader(px, py, pw);
		int bodyTop = py + HEADER_H + 8, bodyH = ph - HEADER_H - 8;
		if (page == 0) {
			drawMods(ctx, px, bodyTop, pw, bodyH, off);
		} else {
			if (getFocused() != null) setFocused(null);
			ui.surface(px, bodyTop, pw, bodyH, 12);
			drawPage(px, bodyTop, pw, bodyH, off);
		}
		drawTooltip(now);

		UpdateDialog.render(ui, viewW, viewH);
		ui.poll();

		// focus the search box when it is clicked, release it when something else is
		if (page == 0 && ui.leftPressed) {
			boolean inside = ui.mx >= sx && ui.mx < sx + sw && ui.my >= sy && ui.my < sy + sh;
			if (inside && !UpdateDialog.isOpen()) setFocused(search);
			else setFocused(null);
		}
		ctx.getMatrices().popMatrix();
	}

	private void drawHeader(int px, int py, int pw) {
		int accent = Theme.accent();
		// logo lock-up: the orbiting R, then "Ray" in white and "Client" in the accent
		int lh = 28;
		Logo.draw(ui.ctx, px + 16, py + (HEADER_H - lh) / 2, lh, 1f);
		int lx = px + 16 + Logo.width(lh) + 9, ly = py + (HEADER_H - 10) / 2;
		int rw = Math.round(ui.headWidth("Ray") * 1.25f);
		ui.scaledHead("Ray", lx, ly, 1.25f, Theme.TEXT);
		ui.scaledHead("Client", lx + rw + 4, ly, 1.25f, Theme.DIM);

		// page tabs: a glass capsule with a highlight that glides to the selected tab
		int padX = 14, th = 26, ty = py + (HEADER_H - th) / 2;
		int total = 0;
		int[] widths = new int[PAGES.length];
		for (int i = 0; i < PAGES.length; i++) {
			widths[i] = ui.w(nice(PAGES[i])) + padX * 2;
			total += widths[i];
		}
		int trackW = total + 8;
		int trackX = Math.max(px + 176, px + (pw - trackW) / 2);
		ui.rrect(trackX, ty, trackW, th, 6, Ui.white(0.045f));
		ui.ring(trackX, ty, trackW, th, 6, Ui.white(0.10f));
		int x = trackX + 4;
		int selX = x, selW = widths[0];
		for (int i = 0; i < PAGES.length; i++) {
			if (i == page) {
				selX = x;
				selW = widths[i];
			}
			x += widths[i];
		}
		float gx = ui.animTo("tabx", selX), gw = ui.animTo("tabw", selW);
		int ix = Math.round(gx), iw = Math.round(gw);
		ui.rrect(ix, ty + 3, iw, th - 6, 5, Ui.white(0.14f));
		x = trackX + 4;
		for (int i = 0; i < PAGES.length; i++) {
			final int idx = i;
			float hv = ui.anim("pgh" + i, ui.over(x, ty, widths[i], th));
			float sv = ui.anim("pgs" + i, page == i);
			ui.text(nice(PAGES[i]), x + padX, ty + 9, Theme.mix(Theme.mix(Theme.MUTE, Theme.DIM, hv), 0xFFFFFFFF, sv));
			ui.click("page" + i, x, ty, widths[i], th, () -> setPage(idx));
			x += widths[i];
		}

		// version + close
		ui.right("v" + RayClient.VERSION, px + pw - 52, py + (HEADER_H - 8) / 2, Theme.MUTE);
		int cx = px + pw - 40, cy = py + (HEADER_H - 26) / 2;
		boolean chov = ui.over(cx, cy, 26, 26);
		float cv = ui.anim("closebtn", chov);
		ui.rrect(cx, cy, 26, 26, 6, Theme.mix(Ui.white(0.06f), Theme.fade(Theme.BAD, 0.55f), cv));
		ui.closeIcon(cx + 9, cy + 9, Theme.mix(Theme.DIM, 0xFFFFFFFF, cv));
		ui.click("close", cx, cy, 26, 26, this::close);
	}

	// ---------- MODS page ----------

	private boolean matches(Features.Card c, String q) {
		switch (filter) {
			case "ON":
				if (!c.on.getAsBoolean()) return false;
				break;
			case "FAV":
				if (!c.favorite()) return false;
				break;
			case "NEW":
				if (!NEW_IDS.contains(c.id)) return false;
				break;
			case "HUD":
				if (!c.keywords.contains("hud module")) return false;
				break;
			case "MECHANIC":
				if (!c.category.equalsIgnoreCase("Gameplay")) return false;
				break;
			case "ALL":
				break;
			default:
				if (!c.category.equalsIgnoreCase(filter)) return false;
		}
		return q.isEmpty() || c.keywords.contains(q) || c.desc.toLowerCase().contains(q);
	}

	private int chipColor(String chip) {
		return chip.equals("FAV") ? 0xFFFF7A9A : Theme.accent();
	}

	private int filterChip(int x, int y, String label) {
		boolean active = filter.equals(label);
		int w = ui.w(nice(label)) + 14, h = 20, cr = 5;
		float hv = ui.anim("fc" + label, ui.over(x, y, w, h));
		float on = ui.anim("fcon" + label, active);
		if (on > 0.05f) ui.glow(x, y, w, h, cr, Theme.accent(), on * 0.5f);
		if (on < 0.98f) {
			ui.rrect(x, y, w, h, cr, Ui.white((0.07f + 0.05f * hv) * (1f - on)));
			if (hv > 0.05f) ui.ring(x, y, w, h, cr, Ui.white(0.16f * hv * (1f - on)));
		}
		if (on > 0.02f) {
			ui.rrect(x, y, w, h, cr, Theme.fade(Theme.accent(), on));
		}
		ui.text(nice(label), x + 7, y + 6, Theme.mix(Theme.mix(Theme.DIM, 0xFFFFFFFF, hv), Ui.onAccent(), on));
		ui.click("fchip" + label, x, y, w, h, () -> setFilter(label));
		return w;
	}

	private void drawMods(DrawContext ctx, int px, int top, int pw, int bodyH, int off) {
		RayConfig cfg = RayConfig.get();
		drawSidebar(px, top, SIDE_W, bodyH);

		int cardX = px + SIDE_W + 8, cardW = pw - SIDE_W - 8;
		ui.surface(cardX, top, cardW, bodyH, 12);
		int left = cardX + 14, innerW = cardW - 28;
		int bodyY = top + 14, innerH = bodyH - 28;

		// search pill (right), view switch, then the chips (wrapping)
		sw = Math.min(116, innerW / 5);
		sh = 22;
		sx = left + innerW - sw;
		sy = bodyY;
		float focus = ui.anim("searchfocus", search.isFocused());
		float shv = ui.anim("searchhov", ui.over(sx, sy, sw, sh));
		if (focus > 0.05f) ui.glow(sx, sy, sw, sh, sh / 2, Theme.accent(), focus * 0.7f);
		ui.rrect(sx, sy, sw, sh, 5, Ui.white(0.045f + 0.03f * Math.max(focus, shv)));
		ui.ring(sx, sy, sw, sh, 5, Theme.mix(Ui.white(0.10f + 0.08f * shv), Theme.fade(Theme.accent(), 0.9f), focus));
		ui.searchIcon(sx + 9, sy + 7, Theme.mix(Theme.MUTE, Theme.DIM, Math.max(focus, shv)));
		search.setX(sx + 24);
		search.setY(sy + 7);
		search.setWidth(sw - 32);
		search.render(ctx, ui.mx, ui.my, 0f);

		int vx = sx - 8 - 28;
		float vv = ui.anim("viewsw", ui.over(vx, sy, 28, sh));
		ui.rrect(vx, sy, 28, sh, 5, Ui.white(0.05f + 0.05f * vv));
		ui.ring(vx, sy, 28, sh, 5, Theme.mix(Ui.white(0.11f), Theme.fade(Theme.accent(), 0.7f), vv * 0.7f));
		if (cfg.listView) ui.listIcon(vx + 9, sy + 8, Theme.mix(Theme.DIM, 0xFFFFFFFF, vv));
		else ui.dotsIcon(vx + 10, sy + 7, Theme.mix(Theme.DIM, 0xFFFFFFFF, vv));
		ui.click("viewsw", vx, sy, 28, sh, () -> {
			cfg.listView = !cfg.listView;
			resetScroll();
			ui.resetAppear();
		});

		int chipAreaW = vx - 10 - left;
		int cx = left, cy = bodyY + 1, rows = 1;
		for (String chip : CHIPS) {
			int cw = ui.w(nice(chip)) + 14;
			if (cx + cw > left + chipAreaW && cx > left) {
				cx = left;
				cy += 24;
				rows++;
			}
			cx += filterChip(cx, cy, chip) + 4;
		}

		// filter, then sort (cached: rebuilt only when something changed, not every frame)
		long nowMs = System.currentTimeMillis();
		String key = filter + "|" + query + "|" + cfg.sortMode;
		if (!key.equals(shownKey) || nowMs - shownAt > 300L) {
			shownKey = key;
			shownAt = nowMs;
			String q = query.trim().toLowerCase();
			List<Features.Card> fresh = new ArrayList<>();
			int tot = 0, en = 0;
			for (Features.Card c : Features.cards()) {
				tot++;
				if (c.on.getAsBoolean()) en++;
				if (matches(c, q)) fresh.add(c);
			}
			Comparator<Features.Card> byName = Comparator.comparing(c -> c.name.toLowerCase());
			switch (cfg.sortMode) {
				case 1 -> fresh.sort(byName);
				case 2 -> fresh.sort(Comparator.<Features.Card, Boolean>comparing(c -> !c.on.getAsBoolean()).thenComparing(byName));
				case 3 -> fresh.sort(Comparator.<Features.Card, Boolean>comparing(c -> !c.favorite()).thenComparing(byName));
				default -> { }
			}
			shownCache = fresh;
			totalCount = tot;
			enabledCount = en;
		}
		List<Features.Card> shown = shownCache;
		int total = totalCount, enabled = enabledCount;

		// layout: tiles in a grid, or compact rows
		boolean list = cfg.listView;
		int gy = bodyY + rows * 24 + 10;
		int footH = 28;
		int gh = bodyY + innerH - footH - gy;
		int gw = innerW - 8;
		int cols = list ? 1 : cfg.menuCols > 0 ? cfg.menuCols : Math.max(2, Math.min(5, (gw + GAP) / (172 + GAP)));
		int itemH = list ? 34 : TILE_H;
		int gap = list ? 6 : GAP;
		int cardWd = (gw - (cols - 1) * gap) / cols;
		int gridRows = (shown.size() + cols - 1) / cols;
		int contentH = Math.max(0, gridRows * (itemH + gap) - gap) + 14;
		scrollMax = Math.max(0, contentH - gh);
		scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget));
		off = Math.max(0, Math.min(scrollMax, off));

		ui.pushClip(left - 4, gy, gw + 8, gh);
		for (int i = 0; i < shown.size(); i++) {
			int x = left + (i % cols) * (cardWd + gap);
			int y = gy + 6 + (i / cols) * (itemH + gap) - off;
			if (y + itemH < gy || y > gy + gh) continue;
			if (list) drawRow(shown.get(i), x, y, cardWd, itemH, i);
			else drawCard(shown.get(i), x, y, cardWd, itemH, i);
		}
		if (shown.isEmpty()) {
			ui.plate(left + gw / 2 - 16, gy + 14, 32, 0xFF8A8A98, 0.3f);
			ui.searchIcon(left + gw / 2 - 4, gy + 26, Theme.DIM);
			ui.center("Nothing matches", left + gw / 2, gy + 56, Theme.TEXT);
			ui.center(filter.equals("FAV") ? "Click the heart on a module to favourite it."
					: filter.equals("ON") ? "No modules are enabled yet." : "Try a different search or filter.",
				left + gw / 2, gy + 70, Theme.MUTE);
		}
		ui.popClip();
		if (scrollMax > 0) {
			// soft fades where content is cut off
			int edge = 0xFF0B0B10;
			if (off > 2) ui.ctx.fillGradient(left - 4, gy, left + gw + 4, gy + 14, Theme.fade(edge, 0.60f), Theme.fade(edge, 0f));
			if (off < scrollMax - 2) ui.ctx.fillGradient(left - 4, gy + gh - 14, left + gw + 4, gy + gh, Theme.fade(edge, 0f), Theme.fade(edge, 0.60f));
		}
		ui.scrollbar(left + innerW - 4, gy, gh, gh, contentH, off);

		// footer: counts on the left, sort on the right
		int fy = bodyY + innerH - 16;
		ui.rect(left, fy - 6, innerW, 1, Theme.fade(Theme.STROKE, 0.7f));
		ui.circle(left + 3, fy + 6, 2, Theme.accent());
		ui.text(enabled + " on", left + 10, fy + 3, Theme.DIM);
		ui.text(shown.size() + " of " + total + " shown", left + 12 + ui.w(enabled + " on") + 12, fy + 3, Theme.MUTE);
		String sortLabel = "Sort: " + nice(SORTS[Math.max(0, Math.min(SORTS.length - 1, cfg.sortMode))]);
		int ws = ui.w(sortLabel) + 14;
		ui.chip(left + innerW - ws, fy, sortLabel, false, () -> {
			cfg.sortMode = (cfg.sortMode + 1) % SORTS.length;
			resetScroll();
		});

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

	// ---------- sidebar (profiles) ----------

	private void drawSidebar(int x, int y, int w, int h) {
		sideX = x;
		sideY = y;
		sideW = w;
		sideH = h;
		int accent = Theme.accent();
		ui.surface(x, y, w, h, 12);

		if (profilesDirty || System.currentTimeMillis() - profilesTime > 1500L) {
			profileNames = Profiles.list();
			if (profileNames.remove("Default")) profileNames.add(0, "Default");
			profilesTime = System.currentTimeMillis();
			profilesDirty = false;
		}

		ui.headText("Profiles", x + 14, y + 13, Theme.TEXT);
		String cnt = String.valueOf(profileNames.size());
		int cw = ui.w(cnt) + 10;
		ui.rrect(x + w - 14 - cw, y + 11, cw, 14, 7, Ui.white(0.08f));
		ui.center(cnt, x + w - 14 - cw / 2, y + 14, Theme.DIM);

		int btnH = 24;
		int editY = y + h - 12 - btnH;
		int saveY = editY - 6 - btnH;
		int listTop = y + 34, listBottom = saveY - 10;
		int listH = Math.max(0, listBottom - listTop);
		int rowH = 28, rowGap = 4;
		int contentH = profileNames.size() * (rowH + rowGap);
		sideMax = Math.max(0, contentH - listH);
		sideTarget = Math.max(0, Math.min(sideMax, sideTarget));
		int soff = Math.max(0, Math.min(sideMax, Math.round(sideCur)));

		ui.pushClip(x + 6, listTop, w - 12, listH);
		for (int i = 0; i < profileNames.size(); i++) {
			final String pn = profileNames.get(i);
			int ry = listTop + i * (rowH + rowGap) - soff;
			if (ry + rowH < listTop || ry > listBottom) continue;
			int rx = x + 10, rw = w - 20 - (sideMax > 0 ? 4 : 0);
			boolean sel = pn.equals(activeProfile);
			boolean hov = ui.over(rx, ry, rw, rowH);
			float hv = ui.anim("prof" + pn, hov);
			float sv = ui.anim("profsel" + pn, sel);
			ui.rrect(rx, ry, rw, rowH, 6, Ui.white(0.05f + 0.04f * hv + 0.06f * sv));
			if (sv > 0.05f) ui.ring(rx, ry, rw, rowH, 6, Theme.fade(accent, 0.7f * sv));
			// initial on a little plate
			ui.plate(rx + 5, ry + 5, 18, sel ? accent : 0xFF8A8A98, Math.max(sv, 0.25f));
			String initial = pn.isEmpty() ? "?" : pn.substring(0, 1).toUpperCase();
			ui.center(initial, rx + 14, ry + 10, Theme.mix(Theme.DIM, 0xFFFFFFFF, Math.max(hv, sv)));
			ui.text(ui.fit(pn, rw - 32 - (hv > 0.05f ? 36 : 0)), rx + 29, ry + 10, Theme.mix(Theme.DIM, 0xFFFFFFFF, Math.max(hv, sv)));
			if (sv > 0.02f && hv < 0.05f) ui.checkIcon(rx + rw - 15, ry + 11, Theme.fade(accent, sv));
			if (hv > 0.02f) {
				int ix = rx + rw - 19;
				boolean dh = ui.over(ix, ry + 6, 14, 16);
				ui.closeIcon(ix + 3, ry + 11, Theme.fade(dh ? Theme.BAD : Theme.DIM, hv));
				ui.click("profdel" + pn, ix, ry + 6, 14, 16, () -> {
					Profiles.delete(pn);
					if (pn.equals(activeProfile)) activeProfile = null;
					profilesDirty = true;
					Toasts.push("Profile deleted: " + pn);
				});
				int ox = ix - 17;
				boolean oh = ui.over(ox, ry + 6, 14, 16);
				ui.arrowUpIcon(ox + 4, ry + 11, Theme.fade(oh ? accent : Theme.DIM, hv));
				ui.click("profsave" + pn, ox, ry + 6, 14, 16, () -> {
					Profiles.save(pn);
					activeProfile = pn;
					Toasts.push("Profile updated: " + pn);
				});
			}
			ui.click("profload" + pn, rx, ry, rw, rowH, () -> {
				if (Profiles.load(pn)) {
					activeProfile = pn;
					ui.resetAppear();
					Toasts.push("Profile loaded: " + pn);
				} else {
					Toasts.push("Could not load " + pn);
				}
			});
		}
		if (profileNames.isEmpty()) ui.note("No profiles yet. Save your setup to switch layouts in one click.", x + 14, listTop + 4, w - 28);
		ui.popClip();
		if (sideMax > 0) ui.scrollbar(x + w - 9, listTop, listH, listH, contentH, soff);

		ui.pill(x + 10, saveY, w - 20, btnH, "Save as new profile", 0xFFB8B8C8, true, () -> {
			String name = Profiles.nextName();
			if (Profiles.save(name)) {
				activeProfile = name;
				profilesDirty = true;
				Toasts.push("Profile saved: " + name);
			}
		});
		boolean inGame = client.world != null;
		if (inGame) ui.primary(x + 10, editY, w - 20, btnH, "Edit HUD layout", () -> client.setScreen(new HudEditorScreen()));
		else ui.pill(x + 10, editY, w - 20, btnH, "Join a world to edit", accent, false, () -> {});
	}

	// ---------- tooltip ----------

	private void drawTooltip(long now) {
		boolean show = hovCard != null && now - tipSince > 380L && !ui.left && !UpdateDialog.isOpen();
		float a = ui.anim("tipvis", show);
		if (hovCard == null || a < 0.03f) return;
		Features.Card c = hovCard;
		int maxW = 190;
		List<String> lines = ui.wrap(c.desc, maxW);
		String live = c.on.getAsBoolean() ? previewFor(c, now) : null;
		int tw = Math.max(ui.w(c.name), ui.w("Click: toggle  -  Right-click: settings"));
		for (String l : lines) tw = Math.max(tw, ui.w(l));
		int w = tw + 22, h = 42 + lines.size() * 11 + (live != null ? 13 : 0);
		int tx = ui.mx + 14, ty = ui.my + 16;
		if (tx + w > viewW - 4) tx = ui.mx - w - 10;
		if (ty + h > viewH - 4) ty = viewH - h - 4;
		int accent = Theme.accent();
		ui.shadow(tx, ty, w, h, 8);
		ui.glassPanel(tx, ty, w, h, 9, Theme.fade(0xFF26262F, 0.97f * a), Theme.fade(0xFF0E0E13, 0.97f * a), 0.34f * a, 0.09f * a);
		ui.headText(c.name, tx + 11, ty + 9, Theme.fade(0xFFFFFFFF, a));
		int yy = ty + 22;
		for (String l : lines) {
			ui.text(l, tx + 11, yy, Theme.fade(Theme.DIM, a));
			yy += 11;
		}
		if (live != null) {
			ui.text("Now: " + live, tx + 11, yy + 2, Theme.fade(accent, a));
			yy += 13;
		}
		ui.text("Click: toggle  -  Right-click: settings", tx + 11, yy + 5, Theme.fade(Theme.MUTE, a));
	}

	/** The live value for a module (refreshed a few times a second). */
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

	// ---------- tiles ----------

	private void toggleCard(Features.Card c) {
		c.flip.run();
		RayConfig.save();
		flashAt.put(c.id, System.currentTimeMillis());
		shownAt = 0L;
	}

	/**
	 * One module tile: an icon plate, the name, a status line (ON with the live value, or OFF) and a settings cog.
	 * The border is green when the module is on and red when it is off.
	 */
	private void drawCard(Features.Card c, int x, int y, int w, int h, int index) {
		float ap = ui.appear("cardin" + c.id, Math.min(index, 15) * 16);
		if (ap < 0.02f) return;
		y += Math.round((1f - ap) * 10f);
		boolean hov = ui.over(x, y, w, h);
		if (hov) hovCard = c;
		boolean on = c.on.getAsBoolean();
		float hv = ui.anim("card" + c.id, hov), ov = ui.anim("cardon" + c.id, on);
		int accent = Theme.accent();
		long now = System.currentTimeMillis();
		int r = 6, pad = 9;

		ui.rrect(x, y, w, h, r, Theme.fade(Ui.white(0.055f + 0.035f * hv), ap));
		if (hv > 0.04f) ui.ring(x, y, w, h, r, Theme.fade(Ui.white(0.16f * hv), ap));
		Long fl = flashAt.get(c.id);
		if (fl != null) {
			float ft = (now - fl) / 420f;
			if (ft >= 1f) flashAt.remove(c.id);
			else {
				int e = Math.round(Theme.easeOutCubic(ft) * 6f);
				ui.ring(x - e, y - e, w + 2 * e, h + 2 * e, r + e, Theme.fade(accent, 0.7f * (1f - ft)));
			}
		}

		// header: icon plate, name and category
		ui.plate(x + pad, y + pad, 34, on ? accent : 0xFF8A8A98, 0.2f + 0.8f * ov);
		if (ap > 0.5f) ui.ctx.drawItem(c.icon(), x + pad + 9, y + pad + 9);
		int tx = x + pad + 34 + 9, tw = w - (tx - x) - pad - 14;
		ui.text(ui.fit(c.name, tw), tx, y + pad + 4, Theme.fade(Theme.mix(Theme.DIM, 0xFFFFFFFF, 0.55f + 0.45f * Math.max(ov, hv)), ap));
		ui.text(c.category, tx, y + pad + 17, Theme.fade(Theme.MUTE, ap));
		if (NEW_IDS.contains(c.id)) ui.badge(x + w - pad - 24, y + pad, "NEW", Theme.fade(accent, ap), Theme.fade(Ui.onAccent(), ap));

		// what it does, up to two lines
		java.util.List<String> lines = ui.wrap(c.desc, w - pad * 2);
		for (int i = 0; i < Math.min(2, lines.size()); i++) {
			String ln = lines.get(i);
			if (i == 1 && lines.size() > 2) ln = ui.fit(ln + "...", w - pad * 2);
			ui.text(ln, x + pad, y + pad + 42 + i * 10, Theme.fade(Theme.DIM, 0.85f * ap));
		}

		// footer: the switch, and a link to the settings
		int fy = y + h - 26;
		ui.rect(x + pad, fy, w - pad * 2, 1, Theme.fade(0xFFFFFFFF, 0.07f * ap));
		float k = ui.anim("rowk" + c.id, on);
		ui.switchControl(x + pad, fy + 5, Theme.clamp01(k) * ap);
		ui.text(on ? "On" : "Off", x + pad + 34 + 8, fy + 10, Theme.fade(on ? accent : Theme.MUTE, ap));
		ui.hit("ctog" + c.id, x + pad - 2, fy + 2, 80, 24, () -> toggleCard(c), null, null);

		String st = "Settings";
		int sw = ui.w(st) + 12;
		int sx = x + w - pad - sw;
		float sh = ui.anim("cset" + c.id, ui.over(sx - 2, fy + 2, sw + 4, 24));
		ui.text(st, sx, fy + 10, Theme.fade(Theme.mix(Theme.DIM, 0xFFFFFFFF, sh), ap));
		ui.chevron(sx + ui.w(st) + 4 + Math.round(sh * 2f), fy + 11, Theme.fade(Theme.mix(Theme.MUTE, 0xFFFFFFFF, sh), ap));
		ui.hit("cseth" + c.id, sx - 2, fy + 2, sw + 4, 24, () -> client.setScreen(c.settings.apply(this)), null, null);

		// favourite heart, next to the settings link
		boolean fav = c.favorite();
		int hx = sx - 22;
		float fh = ui.anim("cfav" + c.id, ui.over(hx - 2, fy + 2, 18, 24));
		if (fav || hv > 0.05f) ui.heart(hx, fy + 9, Theme.fade(fav ? 0xFFFF7A9A : Theme.mix(0xFF6E6E7C, 0xFFFFFFFF, fh), ap));
		ui.hit("cfavhit" + c.id, hx - 2, fy + 2, 18, 24, c::toggleFavorite, null, null);

		// the rest of the card: click toggles, right-click opens the settings
		ui.hit("card" + c.id, x, y, w, h, () -> toggleCard(c), () -> client.setScreen(c.settings.apply(this)), null);
	}

	/** Compact one-line version of a module for the list view. */
	private void drawRow(Features.Card c, int x, int y, int w, int h, int index) {
		float ap = ui.appear("rowin" + c.id, Math.min(index, 15) * 12);
		if (ap < 0.02f) return;
		boolean hov = ui.over(x, y, w, h);
		if (hov) hovCard = c;
		boolean on = c.on.getAsBoolean();
		float hv = ui.anim("row" + c.id, hov), ov = ui.anim("rowon" + c.id, on);
		int accent = Theme.accent();
		ui.rrect(x, y, w, h, 6, Theme.fade(Ui.white(0.055f + 0.035f * hv + 0.03f * ov), ap));
		if (hv > 0.04f) ui.ring(x, y, w, h, 6, Theme.fade(Ui.white(0.16f * hv), ap));
		int ps = h - 10;
		ui.plate(x + 5, y + 5, ps, on ? accent : 0xFF8A8A98, 0.25f + 0.75f * ov);
		if (ap > 0.5f) ui.ctx.drawItem(c.icon(), x + 5 + (ps - 16) / 2, y + 5 + (ps - 16) / 2);
		ui.text(ui.fit(c.name, Math.max(40, w / 2 - 54)), x + ps + 14, y + (h - 8) / 2, Theme.fade(Theme.mix(Theme.TEXT, 0xFFFFFFFF, hv), ap));
		ui.text(c.category, x + w / 2 + 4, y + (h - 8) / 2, Theme.fade(Theme.mix(Theme.MUTE, accent, ov * 0.7f), ap));

		// right side: [switch] [cog] [heart]
		int hx = x + w - 22;
		boolean hh = ui.over(hx, y + 5, 16, h - 10);
		ui.heart(hx + 4, y + h / 2 - 3, Theme.fade(c.favorite() ? 0xFFFF7A9A : Theme.mix(0xFF4A4A58, Theme.DIM, hh ? 1f : 0f), ap));
		ui.hit("rfav" + c.id, hx, y + 5, 16, h - 10, c::toggleFavorite, null, null);

		int gx = hx - 28;
		float gv = ui.anim("rowgear" + c.id, ui.over(gx, y + 5, 24, h - 10));
		ui.rrect(gx, y + 5, 24, h - 10, 8, Theme.fade(Theme.mix(Ui.white(0.05f), Theme.fade(accent, 0.35f), gv), ap));
		ui.gearIcon(gx + 8, y + h / 2 - 3, Theme.fade(Theme.mix(Theme.DIM, 0xFFFFFFFF, gv), ap));
		ui.hit("rgear" + c.id, gx, y + 5, 24, h - 10, () -> client.setScreen(c.settings.apply(this)), null, null);

		int tx = gx - 44;
		float k = ui.anim("rowk" + c.id, on);
		ui.switchControl(tx, y + h / 2 - 9, Theme.clamp01(k) * ap);
		ui.hit("rtog" + c.id, tx - 3, y + 3, 40, h - 6, () -> toggleCard(c), null, null);

		ui.hit("row" + c.id, x, y, w, h, () -> toggleCard(c), () -> client.setScreen(c.settings.apply(this)), null);
	}

	// ---------- other pages ----------

	private void drawPage(int px, int py, int pw, int ph, int off) {
		RayConfig cfg = RayConfig.get();
		int vx = px + 18, vy = py + 14, vw = pw - 46, vh = ph - 14 - 26;
		ui.pushClip(vx - 3, vy, vw + 6, vh);
		Ui.Form f = new Ui.Form(ui, vx, vy - off, vw);

		switch (page) {
			case 1 -> settingsPage(f, cfg);
			case 2 -> profilesPage(f);
			case 3 -> waypointsPage(f, cfg);
			default -> { }
		}

		int contentH = f.bottom() - (vy - off);
		ui.popClip();
		scrollMax = Math.max(0, contentH - vh);
		scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget));
		ui.scrollbar(px + pw - 14, vy, vh, vh, contentH, off);
		ui.right("ESC to close", px + pw - 18, py + ph - 18, Theme.MUTE);
	}

	private void profilesPage(Ui.Form f) {
		f.section("Quick presets");
		for (Presets.Preset p : Presets.ALL) {
			int[] r = f.full();
			float hv = ui.anim("pre" + p.name(), ui.over(r[0], r[1], r[2], Ui.H));
			ui.inset(r[0], r[1], r[2], Ui.H, 8, hv);
			ui.text(p.name(), r[0] + 10, r[1] + 8, Theme.TEXT);
			int nameW = ui.w(p.name());
			ui.text(ui.fit(p.blurb(), Math.max(20, r[2] - nameW - 100)), r[0] + 10 + nameW + 12, r[1] + 8, Theme.MUTE);
			ui.labelButton(r[0] + r[2] - 58, r[1] + 4, 50, 16, "Apply", Theme.accent(), () -> Presets.apply(p));
		}
		f.note("A preset turns exactly those HUD modules on and every other HUD module off. Your module settings and positions are kept.");

		f.section("Profiles");
		ui.button(f.full(), "+ Save current setup as new profile", () -> {
			String name = Profiles.nextName();
			Profiles.save(name);
			activeProfile = name;
			profilesDirty = true;
			Toasts.push("Profile saved: " + name);
		});
		if (profilesDirty || System.currentTimeMillis() - profilesTime > 1500L) {
			profileNames = Profiles.list();
			if (profileNames.remove("Default")) profileNames.add(0, "Default");
			profilesTime = System.currentTimeMillis();
			profilesDirty = false;
		}
		if (profileNames.isEmpty()) {
			f.note("No profiles yet. Save one to switch between layouts and settings instantly.");
		}
		for (String pn : new ArrayList<>(profileNames)) {
			int[] r = f.full();
			boolean sel = pn.equals(activeProfile);
			ui.inset(r[0], r[1], r[2], Ui.H, 8, sel ? 1f : 0f);
			ui.text(ui.fit(pn, r[2] - 150), r[0] + 10, r[1] + 8, Theme.TEXT);
			int bx = r[0] + r[2] - 124;
			ui.labelButton(bx, r[1] + 4, 40, 16, "Load", Theme.accent(), () -> {
				if (Profiles.load(pn)) activeProfile = pn;
				Toasts.push("Profile loaded: " + pn);
			});
			ui.labelButton(bx + 44, r[1] + 4, 40, 16, "Save", Theme.GOOD, () -> {
				Profiles.save(pn);
				activeProfile = pn;
				Toasts.push("Profile updated: " + pn);
			});
			ui.labelButton(bx + 88, r[1] + 4, 34, 16, "Del", Theme.BAD, () -> {
				Profiles.delete(pn);
				if (pn.equals(activeProfile)) activeProfile = null;
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
			ui.inset(r[0], r[1], r[2], Ui.H, 8, 0f);
			String dimName = w.dim.contains("nether") ? "Nether" : (w.dim.contains("end") ? "End" : "Overworld");
			String label = w.name + "   " + (int) w.x + "  " + (int) w.y + "  " + (int) w.z + "   " + dimName;
			ui.text(ui.fit(label, r[2] - 44), r[0] + 10, r[1] + 8, Theme.TEXT);
			int bx = r[0] + r[2] - 24;
			boolean hov = ui.iconButton(bx, r[1] + 4, 16, 16, 0xFF3A2A2E, Theme.BAD, () -> {
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
		Updater.State us = Updater.state;
		if (us == Updater.State.READY) ui.button(f.half(), "Restart to Update", UpdateDialog::start);
		else if (us == Updater.State.DOWNLOADING) ui.button(f.half(), "Show Progress", UpdateDialog::start);
		else if (us == Updater.State.AVAILABLE || us == Updater.State.ERROR) ui.button(f.half(), "Update Now", UpdateDialog::start);
		else ui.button(f.half(), "Release Page", Updater::openReleasePage);
		f.note("Manual: nothing happens until you press Check Now (or Check for updates on the start menu). Notify: checks github.com/" + Updater.REPO + " about every 6 hours and tells you. Auto: also downloads the update and installs it when you close the game. Nothing about you is sent.");

		f.section("Menu Size");
		int shownScale = pendingScale != null ? pendingScale : cfg.menuScale;
		ui.sliderShown(f.full(), "Menu Size", 50, 200, shownScale, shownScale == 100 ? "Fits window" : shownScale + "%", v -> pendingScale = v);
		int[] sizes = {70, 85, 100, 115, 130, 150};
		int sizeTop = f.reserve(18);
		int cxs = f.x();
		for (int sz : sizes) {
			final int val = sz;
			cxs += ui.chip(cxs, sizeTop, sz == 100 ? "Auto" : sz + "%", shownScale == sz, () -> {
				pendingScale = null;
				cfg.menuScale = val;
				ui.resetAppear();
			}) + 4;
		}
		f.note("100% (AUTO) sizes every Ray menu to fit your window, whatever Minecraft's GUI Scale is. Drag the slider to make menus smaller or larger than that; it applies when you let go.");

		int[] colOpts = {0, 2, 3, 4, 5, 6};
		int colTop = f.reserve(18);
		int ccx = f.x();
		ui.text("Tiles per row", ccx, colTop + 3, Theme.DIM);
		ccx += ui.w("Tiles per row") + 10;
		for (int co : colOpts) {
			final int val = co;
			ccx += ui.chip(ccx, colTop, co == 0 ? "Auto" : String.valueOf(co), cfg.menuCols == co, () -> {
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
		int top = f.reserve(34);
		for (int i = 0; i < Theme.COLORS.length; i++) {
			final int idx = i;
			int sx2 = f.x() + 4 + i * 36;
			int sy2 = top + 3;
			boolean sel = cfg.accent == i && !cfg.rainbow;
			boolean hov = ui.over(sx2, sy2, 28, 28);
			float sv = ui.anim("sw" + i, sel);
			float hvv = ui.anim("swh" + i, hov);
			ui.rrect(sx2, sy2 - Math.round(hvv), 28, 28, 7, Theme.COLORS[i]);
			if (sv > 0.02f) {
				ui.ring(sx2 - 3, sy2 - 3, 34, 34, 9, Theme.fade(0xFFFFFFFF, sv));
				ui.checkIcon(sx2 + 10, sy2 + 11, Theme.fade(0xFF000000, 0.7f * sv));
			} else if (hvv > 0.05f) {
				ui.ring(sx2 - 1, sy2 - 1, 30, 30, 8, Theme.fade(0xFFFFFFFF, 0.5f * hvv));
			}
			ui.click("sw" + i, sx2, sy2, 28, 28, () -> {
				cfg.accent = idx;
				cfg.rainbow = false;
			});
		}
		ui.toggle(f.half(), "Rainbow Accent", cfg.rainbow, () -> cfg.rainbow = !cfg.rainbow);
		ui.toggle(f.half(), "Text Outline", cfg.hudOutline, () -> cfg.hudOutline = !cfg.hudOutline);
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
