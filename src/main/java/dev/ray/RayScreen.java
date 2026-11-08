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
	private static final String[] PAGES = {"MODS", "SETTINGS", "PROFILES", "WAYPOINTS"};
	private static final String[] CHIPS = {"ALL", "NEW", "HUD", "PVP", "VISUAL", "MECHANIC", "EXTRAS", "ON", "FAV"};
	private static final String[] SORTS = {"CATEGORY", "A-Z", "ENABLED", "FAVORITES"};
	/** Modules that get a red NEW tag on their tile and the NEW filter. */
	private static final Set<String> NEW_IDS = Set.of("f_crosshair", "f_sky", "f_lyrics", "f_hitbox", "f_sideshield", "minimap");

	private static final int DESIGN_W = 760, DESIGN_H = 450;
	private static final int HEADER_H = 46, SIDE_W = 156, TILE_H = 36, GAP = 8;

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
		float t = cfg.uiAnimations ? Math.min(1f, (now - openedAt) / 220f) : 1f;
		float open = Theme.easeOutCubic(t);
		if (client.world != null) ctx.fill(0, 0, width, height, Theme.fade(0xFF000000, 0.40f * open));

		// the menu is laid out in a virtual screen and scaled so it always fits the window
		float sc = UiScale.fit(width, height, DESIGN_W, DESIGN_H, cfg.menuScale);
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
		int px = (viewW - pw) / 2, py = (viewH - ph) / 2 + Math.round((1f - open) * 16f);
		ui.shadow(px, py, pw, ph, 12);
		ui.panelGrad(px, py, pw, ph, 12, ui.glass(0xFF13161B), ui.glass(Theme.PANEL), Theme.STROKE);
		ui.rect(px + 16, py + 1, pw - 32, 1, Theme.fade(0xFFFFFFFF, 0.06f));

		drawHeader(px, py, pw);

		scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-ui.dt * 16f));
		if (Math.abs(scrollTarget - scrollCur) < 0.5f) scrollCur = scrollTarget;
		sideCur += (sideTarget - sideCur) * (1f - (float) Math.exp(-ui.dt * 16f));
		if (Math.abs(sideTarget - sideCur) < 0.5f) sideCur = sideTarget;
		int off = Math.round(scrollCur);

		if (page == 0) {
			drawMods(ctx, px, py, pw, ph, off);
		} else {
			if (getFocused() != null) setFocused(null);
			drawPage(px, py, pw, ph, off);
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
		long now = System.currentTimeMillis();
		// logo lock-up
		Brand.bolt(ui.ctx, px + 26, py + 11, 1, 1.0f, now);
		Brand.word(ui.ctx, "RAY", px + 44, py + 10, 2, 0xFFFFFFFF);
		Brand.word(ui.ctx, "CLIENT", px + 44, py + 27, 1, Theme.accent());

		// page tabs, centred
		int sp = 2, padX = 16, gap = 6, th = 24, ty = py + 11;
		int total = 0;
		for (String p : PAGES) total += ui.spacedWidth(p, sp) + padX * 2 + gap;
		total -= gap;
		int x = Math.max(px + 130, px + (pw - total) / 2);
		for (int i = 0; i < PAGES.length; i++) {
			final int idx = i;
			String p = PAGES[i];
			int w = ui.spacedWidth(p, sp) + padX * 2;
			boolean sel = page == i;
			float hv = ui.anim("pgh" + i, ui.over(x, ty, w, th));
			float sv = ui.anim("pgs" + i, sel);
			int fill = Theme.mix(Theme.mix(0xFF15181E, 0xFF1D2128, hv), 0xFF2A2F3A, sv);
			ui.rrect(x, ty, w, th, 7, fill);
			ui.ring(x, ty, w, th, 7, Theme.mix(Theme.mix(0xFF22262E, Theme.STROKE_HI, hv), 0xFF5A6273, sv));
			ui.spaced(p, x + padX, ty + 8, Theme.mix(Theme.mix(Theme.MUTE, Theme.DIM, hv), 0xFFFFFFFF, sv), sp);
			if (sv > 0.02f) ui.rrect(x + w / 2 - 8, ty + th - 2, 16, 2, 1, Theme.fade(Theme.accent(), sv));
			ui.click("page" + i, x, ty, w, th, () -> setPage(idx));
			x += w + gap;
		}

		ui.right("v" + RayClient.VERSION, px + pw - 48, ty + 8, Theme.MUTE);

		// close
		int cx = px + pw - 38;
		boolean chov = ui.over(cx, ty, 26, 24);
		float cv = ui.anim("closebtn", chov);
		ui.rrect(cx, ty, 26, 24, 7, Theme.mix(0xFF1E1518, 0xFFB42B35, cv));
		ui.ring(cx, ty, 26, 24, 7, Theme.mix(0xFF5A2A31, 0xFFFF6A76, cv));
		ui.closeIcon(cx + 9, ty + 8, Theme.mix(0xFFFF9AA2, 0xFFFFFFFF, cv));
		ui.click("close", cx, ty, 26, 24, this::close);

		ui.rect(px + 12, py + HEADER_H, pw - 24, 1, Theme.fade(Theme.STROKE, 0.9f));
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
		return switch (chip) {
			case "ALL" -> Theme.accent();
			case "NEW", "ON" -> Theme.TILE_ON;
			case "FAV" -> 0xFFFF6A86;
			default -> 0xFF5C86C8;
		};
	}

	private int filterChip(int x, int y, String label) {
		boolean active = filter.equals(label);
		int base = chipColor(label);
		String shownLabel = label;
		int w = ui.spacedWidth(shownLabel, 1) + 18, h = 20;
		float hv = ui.anim("fc" + label, ui.over(x, y, w, h));
		float on = ui.anim("fcon" + label, active);
		int bg = Theme.mix(Theme.mix(0xFF15181E, base, 0.20f + 0.10f * hv), Theme.mix(0xFF15181E, base, 0.80f), on);
		int line = Theme.mix(Theme.mix(Theme.STROKE, base, 0.50f + 0.20f * hv), Theme.mix(base, 0xFFFFFFFF, 0.25f), on);
		ui.rrect(x, y, w, h, 6, bg);
		ui.ring(x, y, w, h, 6, line);
		ui.spaced(label, x + 9, y + 6, Theme.mix(Theme.mix(Theme.DIM, 0xFFFFFFFF, hv), 0xFFFFFFFF, on), 1);
		ui.click("fchip" + label, x, y, w, h, () -> setFilter(label));
		return w;
	}

	private void drawMods(DrawContext ctx, int px, int py, int pw, int ph, int off) {
		RayConfig cfg = RayConfig.get();
		int bodyY = py + HEADER_H + 10, bodyH = ph - HEADER_H - 20;
		drawSidebar(px + 12, bodyY, SIDE_W, bodyH);

		int left = px + 12 + SIDE_W + 12;
		int innerW = px + pw - 12 - left;

		int total = 0, enabled = 0;
		for (Features.Card c : Features.cards()) {
			total++;
			if (c.on.getAsBoolean()) enabled++;
		}

		// search pill (right), view switch, then the chips (wrapping)
		sw = Math.min(150, innerW / 4);
		sh = 20;
		sx = left + innerW - sw;
		sy = bodyY;
		float focus = ui.anim("searchfocus", search.isFocused());
		float shv = ui.anim("searchhov", ui.over(sx, sy, sw, sh));
		ui.rrect(sx, sy, sw, sh, 10, 0xFF0E1014);
		ui.ring(sx, sy, sw, sh, 10, Theme.mix(Theme.mix(Theme.STROKE, Theme.STROKE_HI, shv), Theme.accent(), focus));
		ui.searchIcon(sx + 8, sy + 6, Theme.mix(Theme.MUTE, Theme.DIM, Math.max(focus, shv)));
		search.setX(sx + 22);
		search.setY(sy + 6);
		search.setWidth(sw - 30);
		search.render(ctx, ui.mx, ui.my, 0f);

		int vx = sx - 8 - 26;
		boolean vhov = ui.over(vx, sy, 26, 20);
		float vv = ui.anim("viewsw", vhov);
		ui.rrect(vx, sy, 26, 20, 7, Theme.mix(0xFF15181E, 0xFF1F232B, vv));
		ui.ring(vx, sy, 26, 20, 7, Theme.mix(Theme.STROKE, Theme.STROKE_HI, vv));
		if (cfg.listView) ui.listIcon(vx + 8, sy + 7, Theme.mix(Theme.DIM, 0xFFFFFFFF, vv));
		else ui.dotsIcon(vx + 9, sy + 6, Theme.mix(Theme.DIM, 0xFFFFFFFF, vv));
		ui.click("viewsw", vx, sy, 26, 20, () -> {
			cfg.listView = !cfg.listView;
			resetScroll();
			ui.resetAppear();
		});

		int chipAreaW = vx - 10 - left;
		int cx = left, cy = bodyY, rows = 1;
		for (String chip : CHIPS) {
			int cw = ui.spacedWidth(chip, 1) + 18;
			if (cx + cw > left + chipAreaW && cx > left) {
				cx = left;
				cy += 26;
				rows++;
			}
			cx += filterChip(cx, cy, chip) + 6;
		}

		// filter, then sort
		String q = query.trim().toLowerCase();
		List<Features.Card> shown = new ArrayList<>();
		for (Features.Card c : Features.cards()) if (matches(c, q)) shown.add(c);
		Comparator<Features.Card> byName = Comparator.comparing(c -> c.name.toLowerCase());
		switch (cfg.sortMode) {
			case 1 -> shown.sort(byName);
			case 2 -> shown.sort(Comparator.<Features.Card, Boolean>comparing(c -> !c.on.getAsBoolean()).thenComparing(byName));
			case 3 -> shown.sort(Comparator.<Features.Card, Boolean>comparing(c -> !c.favorite()).thenComparing(byName));
			default -> { }
		}

		// layout: tiles in a grid, or compact rows
		boolean list = cfg.listView;
		int gy = bodyY + rows * 26 + 4;
		int footH = 20;
		int gh = bodyY + bodyH - footH - gy;
		int gw = innerW - 10;
		int cols = list ? 1 : cfg.menuCols > 0 ? cfg.menuCols : Math.max(2, Math.min(5, (gw + GAP) / (170 + GAP)));
		int itemH = list ? 28 : TILE_H;
		int gap = list ? 5 : GAP;
		int cardW = (gw - (cols - 1) * gap) / cols;
		int gridRows = (shown.size() + cols - 1) / cols;
		int contentH = Math.max(0, gridRows * (itemH + gap) - gap) + 6;
		scrollMax = Math.max(0, contentH - gh);
		scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget));
		off = Math.max(0, Math.min(scrollMax, off));

		ui.pushClip(left - 3, gy, gw + 6, gh);
		for (int i = 0; i < shown.size(); i++) {
			int x = left + (i % cols) * (cardW + gap);
			int y = gy + 3 + (i / cols) * (itemH + gap) - off;
			if (y + itemH < gy || y > gy + gh) continue;
			if (list) drawRow(shown.get(i), x, y, cardW, itemH, i);
			else drawCard(shown.get(i), x, y, cardW, itemH, i);
		}
		if (shown.isEmpty()) {
			ui.searchIcon(left + gw / 2 - 4, gy + 14, Theme.MUTE);
			ui.center("Nothing matches.", left + gw / 2, gy + 34, Theme.DIM);
			ui.center(filter.equals("FAV") ? "Click the heart on a module to favourite it."
					: filter.equals("ON") ? "No modules are enabled yet." : "Try a different search or filter.",
				left + gw / 2, gy + 48, Theme.MUTE);
		}
		ui.popClip();
		if (scrollMax > 0) {
			int edge = 0xFF13161B;
			if (off > 2) ui.ctx.fillGradient(left - 3, gy, left + gw + 3, gy + 12, Theme.fade(edge, 0.9f), Theme.fade(edge, 0f));
			if (off < scrollMax - 2) ui.ctx.fillGradient(left - 3, gy + gh - 12, left + gw + 3, gy + gh, Theme.fade(edge, 0f), Theme.fade(edge, 0.9f));
		}
		ui.scrollbar(left + innerW - 5, gy, gh, gh, contentH, off);

		// footer: counts on the left, sort on the right
		int fy = bodyY + bodyH - 15;
		String count = shown.size() + " of " + total + " modules   -   " + enabled + " on";
		ui.text(count, left, fy + 3, Theme.MUTE);
		String sortLabel = "SORT: " + SORTS[Math.max(0, Math.min(SORTS.length - 1, cfg.sortMode))];
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
		ui.rrect(x, y, w, h, 9, 0xFF101318);
		ui.ring(x, y, w, h, 9, Theme.STROKE);

		if (profilesDirty || System.currentTimeMillis() - profilesTime > 1500L) {
			profileNames = Profiles.list();
			if (profileNames.remove("Default")) profileNames.add(0, "Default");
			profilesTime = System.currentTimeMillis();
			profilesDirty = false;
		}

		ui.spaced("PROFILES", x + 12, y + 10, Theme.MUTE, 2);

		int btnH = 22;
		int editY = y + h - 10 - btnH;
		int saveY = editY - 6 - btnH;
		int listTop = y + 26, listBottom = saveY - 8;
		int listH = Math.max(0, listBottom - listTop);
		int rowH = 22, rowGap = 4;
		int contentH = profileNames.size() * (rowH + rowGap);
		sideMax = Math.max(0, contentH - listH);
		sideTarget = Math.max(0, Math.min(sideMax, sideTarget));
		int soff = Math.max(0, Math.min(sideMax, Math.round(sideCur)));

		ui.pushClip(x + 6, listTop, w - 12, listH);
		for (int i = 0; i < profileNames.size(); i++) {
			final String pn = profileNames.get(i);
			int ry = listTop + i * (rowH + rowGap) - soff;
			if (ry + rowH < listTop || ry > listBottom) continue;
			int rx = x + 8, rw = w - 16 - (sideMax > 0 ? 4 : 0);
			boolean sel = pn.equals(activeProfile);
			boolean hov = ui.over(rx, ry, rw, rowH);
			float hv = ui.anim("prof" + pn, hov);
			float sv = ui.anim("profsel" + pn, sel);
			int accent = Theme.accent();
			ui.rrect(rx, ry, rw, rowH, 6, Theme.mix(Theme.mix(0xFF171A20, 0xFF1F232B, hv), Theme.mix(0xFF171A20, accent, 0.24f), sv));
			ui.ring(rx, ry, rw, rowH, 6, Theme.mix(Theme.mix(0xFF262A33, Theme.STROKE_HI, hv), accent, sv * 0.9f));
			if (sv > 0.02f) ui.circle(rx + 9, ry + rowH / 2, 2, Theme.fade(accent, sv));
			ui.spaced(ui.fit(pn.toUpperCase(), rw - 56), rx + 9 + Math.round(sv * 6f), ry + 7, Theme.mix(Theme.DIM, 0xFFFFFFFF, Math.max(hv, sv)), 1);
			if (hv > 0.02f) {
				int ix = rx + rw - 18;
				boolean dh = ui.over(ix, ry + 4, 14, 14);
				ui.closeIcon(ix + 3, ry + 8, Theme.fade(dh ? 0xFFFF6A76 : Theme.DIM, hv));
				ui.click("profdel" + pn, ix, ry + 4, 14, 14, () -> {
					Profiles.delete(pn);
					if (pn.equals(activeProfile)) activeProfile = null;
					profilesDirty = true;
					Toasts.push("Profile deleted: " + pn);
				});
				int ox = ix - 17;
				boolean oh = ui.over(ox, ry + 4, 14, 14);
				ui.arrowUpIcon(ox + 4, ry + 8, Theme.fade(oh ? Theme.accent() : Theme.DIM, hv));
				ui.click("profsave" + pn, ox, ry + 4, 14, 14, () -> {
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
		if (profileNames.isEmpty()) ui.note("No profiles yet. Save your setup to switch layouts in one click.", x + 12, listTop + 4, w - 24);
		ui.popClip();
		if (sideMax > 0) ui.scrollbar(x + w - 8, listTop, listH, listH, contentH, soff);

		ui.pill(x + 8, saveY, w - 16, btnH, "SAVE AS NEW PROFILE", 0xFF8FA3C8, true, () -> {
			String name = Profiles.nextName();
			if (Profiles.save(name)) {
				activeProfile = name;
				profilesDirty = true;
				Toasts.push("Profile saved: " + name);
			}
		});
		boolean inGame = client.world != null;
		ui.pill(x + 8, editY, w - 16, btnH, inGame ? "EDIT HUD LAYOUT" : "JOIN A WORLD TO EDIT", Theme.accent(), inGame, () -> client.setScreen(new HudEditorScreen()));
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
		int w = tw + 20, h = 40 + lines.size() * 11 + (live != null ? 13 : 0);
		int tx = ui.mx + 14, ty = ui.my + 16;
		if (tx + w > viewW - 4) tx = ui.mx - w - 10;
		if (ty + h > viewH - 4) ty = viewH - h - 4;
		int accent = Theme.accent();
		ui.shadow(tx, ty, w, h, 6);
		ui.panelGrad(tx, ty, w, h, 6, Theme.fade(0xFF191C22, 0.98f * a), Theme.fade(0xFF111318, 0.98f * a), Theme.fade(Theme.mix(Theme.STROKE_HI, accent, 0.5f), a));
		ui.text(c.name, tx + 10, ty + 8, Theme.fade(0xFFFFFFFF, a));
		int yy = ty + 21;
		for (String l : lines) {
			ui.text(l, tx + 10, yy, Theme.fade(Theme.DIM, a));
			yy += 11;
		}
		if (live != null) {
			ui.text("Now: " + live, tx + 10, yy + 2, Theme.fade(accent, a));
			yy += 13;
		}
		ui.text("Click: toggle  -  Right-click: settings", tx + 10, yy + 4, Theme.fade(Theme.MUTE, a));
	}

	/** The live value for a module (refreshed a few times a second, only for the one under the mouse). */
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
	}

	/** One module tile: icon, name, and a settings cog. Border is green when the module is on, red when it is off. */
	private void drawCard(Features.Card c, int x, int y, int w, int h, int index) {
		float ap = ui.appear("cardin" + c.id, Math.min(index, 15) * 16);
		if (ap < 0.02f) return;
		y += Math.round((1f - ap) * 10f);
		boolean hov = ui.over(x, y, w, h);
		if (hov) hovCard = c;
		boolean on = c.on.getAsBoolean();
		float hv = ui.anim("card" + c.id, hov), ov = ui.anim("cardon" + c.id, on);
		int ly = y - Math.round(hv);
		int border = Theme.mix(Theme.TILE_OFF, Theme.TILE_ON, ov);
		long now = System.currentTimeMillis();

		if (hv > 0.02f) ui.glow(x, ly, w, h, 8, border, hv * 0.8f);
		int bg = Theme.mix(Theme.mix(Theme.TILE_BG, Theme.TILE_BG_HOVER, hv), Theme.mix(Theme.TILE_BG, border, 0.10f), 0.55f);
		ui.rrectGrad(x, ly, w, h, 8, Theme.fade(Theme.mix(bg, 0xFFFFFFFF, 0.03f), ap), Theme.fade(bg, ap));
		ui.ring(x, ly, w, h, 8, Theme.fade(border, (0.62f + 0.38f * hv) * ap));

		// a quick expanding ring when the module is switched
		Long fl = flashAt.get(c.id);
		if (fl != null) {
			float ft = (now - fl) / 420f;
			if (ft >= 1f) flashAt.remove(c.id);
			else {
				int e = Math.round(Theme.easeOutCubic(ft) * 7f);
				ui.ring(x - e, ly - e, w + 2 * e, h + 2 * e, 8 + e, Theme.fade(border, 0.7f * (1f - ft)));
			}
		}

		if (ap > 0.5f) ui.ctx.drawItem(c.icon(), x + 10, ly + (h - 16) / 2);
		int tx = x + 34, tw = w - 34 - 50;
		String name = ui.fit(c.name, tw);
		int ty = ly + (h - 8) / 2;
		ui.text(name, tx, ty, Theme.fade(Theme.mix(0xFFC9CFDB, 0xFFFFFFFF, Math.max(hv, ov * 0.6f)), ap));
		if (NEW_IDS.contains(c.id)) ui.scaledText("NEW", tx + ui.w(name) + 2, ty - 4, 0.62f, Theme.fade(0xFFFF4D5E, ap), false);

		// live value (FPS, coordinates...) for enabled modules, dimmed and right-aligned
		if (on && ap > 0.9f) {
			String live = previewFor(c, now);
			if (live != null && !live.isEmpty()) {
				int room = w - 34 - 50 - ui.w(name) - 12;
				if (room > 24) ui.right(ui.fit(live, room), x + w - 34 - 20, ty, Theme.fade(Theme.mix(Theme.DIM, border, 0.35f), 0.9f * ap));
			}
		}

		// divider + cog
		int dx = x + w - 34;
		ui.rect(dx, ly + 8, 1, h - 16, Theme.fade(Theme.STROKE, ap));
		boolean ghov = ui.over(dx, y, 34, h);
		float gv = ui.anim("gear" + c.id, ghov);
		if (gv > 0.02f) ui.circle(dx + 17, ly + h / 2, 9, Theme.fade(0xFFFFFFFF, 0.07f * gv * ap));
		ui.gearIcon(dx + 12, ly + h / 2 - 4, Theme.fade(Theme.mix(Theme.MUTE, 0xFFFFFFFF, Math.max(gv, hv * 0.5f)), ap));
		ui.hit("gearhit" + c.id, dx, y, 34, h, () -> client.setScreen(c.settings.apply(this)), null, null);

		// favourite heart (shows on hover, or always when favourited)
		boolean fav = c.favorite();
		int hx = dx - 16;
		boolean hhov = ui.over(hx, y + h / 2 - 7, 14, 14);
		float hh = ui.anim("heart" + c.id, hhov);
		if (fav || hv > 0.05f) {
			float vis = fav ? 1f : hv;
			ui.heart(hx + 3, ly + h / 2 - 3, Theme.fade(fav ? 0xFFFF6A86 : Theme.mix(0x66FFFFFF, 0xFFFFFFFF, hh), vis * ap));
		}
		ui.hit("fav" + c.id, hx, y + h / 2 - 7, 14, 14, c::toggleFavorite, null, null);

		// click anywhere else toggles, right-click opens the settings
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
		int border = Theme.mix(Theme.TILE_OFF, Theme.TILE_ON, ov);
		int accent = Theme.accent();
		ui.rrect(x, y, w, h, 7, Theme.fade(Theme.mix(Theme.TILE_BG, Theme.TILE_BG_HOVER, hv), ap));
		ui.ring(x, y, w, h, 7, Theme.fade(border, (0.55f + 0.4f * hv) * ap));
		if (ap > 0.5f) ui.ctx.drawItem(c.icon(), x + 8, y + (h - 16) / 2);
		ui.text(ui.fit(c.name, Math.max(40, w / 2 - 50)), x + 32, y + (h - 8) / 2, Theme.fade(Theme.mix(0xFFC9CFDB, 0xFFFFFFFF, hv), ap));
		ui.text(c.category.toUpperCase(), x + w / 2 + 4, y + (h - 8) / 2, Theme.fade(Theme.mix(Theme.MUTE, accent, ov * 0.7f), ap));

		// right side: [switch] [cog] [heart]
		int hx = x + w - 22;
		boolean hh = ui.over(hx, y + 5, 16, 18);
		ui.heart(hx + 4, y + h / 2 - 3, Theme.fade(c.favorite() ? 0xFFFF6A86 : Theme.mix(0xFF3A425A, Theme.DIM, hh ? 1f : 0f), ap));
		ui.hit("rfav" + c.id, hx, y + 5, 16, 18, c::toggleFavorite, null, null);

		int gx = hx - 26;
		float gv = ui.anim("rowgear" + c.id, ui.over(gx, y + 4, 22, h - 8));
		ui.rrect(gx, y + 4, 22, h - 8, 6, Theme.fade(Theme.mix(0xFF1D2128, accent, gv * 0.6f), ap));
		ui.gearIcon(gx + 6, y + h / 2 - 4, Theme.fade(Theme.mix(Theme.DIM, 0xFFFFFFFF, gv), ap));
		ui.hit("rgear" + c.id, gx, y + 4, 22, h - 8, () -> client.setScreen(c.settings.apply(this)), null, null);

		int tx = gx - 40;
		float k = ui.anim("rowk" + c.id, on);
		ui.rrect(tx, y + h / 2 - 7, 30, 14, 7, Theme.fade(Theme.mix(0xFF2A303B, Theme.TILE_ON, k), ap));
		ui.rrect(tx + 2 + Math.round(k * 16), y + h / 2 - 5, 10, 10, 5, Theme.fade(0xFFFFFFFF, ap));
		ui.hit("rtog" + c.id, tx - 3, y + 3, 36, h - 6, () -> toggleCard(c), null, null);

		ui.hit("row" + c.id, x, y, w, h, () -> toggleCard(c), () -> client.setScreen(c.settings.apply(this)), null);
	}

	// ---------- other pages ----------

	private void drawPage(int px, int py, int pw, int ph, int off) {
		RayConfig cfg = RayConfig.get();
		int vx = px + 16, vy = py + HEADER_H + 12, vw = pw - 42, vh = ph - HEADER_H - 12 - 24;
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
		ui.scrollbar(px + pw - 16, vy, vh, vh, contentH, off);
		ui.right("ESC to close", px + pw - 16, py + ph - 17, Theme.MUTE);
	}

	private void profilesPage(Ui.Form f) {
		f.section("Quick presets");
		for (Presets.Preset p : Presets.ALL) {
			int[] r = f.full();
			float hv = ui.anim("pre" + p.name(), ui.over(r[0], r[1], r[2], Ui.H));
			ui.panel(r[0], r[1], r[2], Ui.H, 6, Theme.mix(Theme.CARD, Theme.CARD_HOVER, hv), Theme.mix(Theme.STROKE, Theme.accent(), hv * 0.45f));
			ui.text(p.name(), r[0] + 10, r[1] + 8, Theme.TEXT);
			int nameW = ui.w(p.name());
			ui.text(ui.fit(p.blurb(), Math.max(20, r[2] - nameW - 100)), r[0] + 10 + nameW + 12, r[1] + 8, Theme.MUTE);
			ui.labelButton(r[0] + r[2] - 58, r[1] + 4, 50, 16, "Apply", 0xFF1E3A8A, () -> Presets.apply(p));
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
			ui.panel(r[0], r[1], r[2], Ui.H, 6, Theme.CARD, sel ? Theme.fade(Theme.accent(), 0.8f) : Theme.STROKE);
			ui.text(ui.fit(pn, r[2] - 150), r[0] + 10, r[1] + 8, Theme.TEXT);
			int bx = r[0] + r[2] - 124;
			ui.labelButton(bx, r[1] + 4, 40, 16, "Load", 0xFF1E3A8A, () -> {
				if (Profiles.load(pn)) activeProfile = pn;
				Toasts.push("Profile loaded: " + pn);
			});
			ui.labelButton(bx + 44, r[1] + 4, 40, 16, "Save", 0xFF14532D, () -> {
				Profiles.save(pn);
				activeProfile = pn;
				Toasts.push("Profile updated: " + pn);
			});
			ui.labelButton(bx + 88, r[1] + 4, 34, 16, "Del", 0xFF7F1D1D, () -> {
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
			ui.panel(r[0], r[1], r[2], Ui.H, 6, Theme.CARD, Theme.STROKE);
			String dimName = w.dim.contains("nether") ? "Nether" : (w.dim.contains("end") ? "End" : "Overworld");
			String label = w.name + "   " + (int) w.x + "  " + (int) w.y + "  " + (int) w.z + "   " + dimName;
			ui.text(ui.fit(label, r[2] - 44), r[0] + 10, r[1] + 8, Theme.TEXT);
			int bx = r[0] + r[2] - 24;
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
			cxs += ui.chip(cxs, sizeTop, sz == 100 ? "AUTO" : sz + "%", shownScale == sz, () -> {
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
