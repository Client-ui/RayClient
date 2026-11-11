package dev.ray;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Base for every Ray settings page: a centred window with a back button, a scrolling body and optional reset. */
public abstract class SettingsScreen extends Screen {
	protected final Screen parent;
	protected final Ui ui = new Ui();
	private final String heading;
	private final long openedAt = System.currentTimeMillis();
	private int scroll = 0, scrollMax = 0, scrollTarget = 0;
	private float scrollCur = 0f;
	private final Map<String, NumberInput> inputs = new HashMap<>();
	private int viewTop, viewBottom;

	protected SettingsScreen(Screen parent, String heading) {
		super(Text.literal(heading));
		this.parent = parent;
		this.heading = heading;
	}

	/** Adds this page's rows. Called every frame inside the scrolling viewport. */
	protected abstract void rows(Ui ui, Ui.Form f);

	protected boolean hasReset() {
		return false;
	}

	protected void onReset() {}

	protected void afterFrame() {}

	/** A row with a label and a box you can type a number into (e.g. 1.33). r = {x, y, width} from the form. */
	public void numberRow(int[] r, String label, String id, float current, float min, float max, Consumer<Float> set) {
		NumberInput in = inputs.get(id);
		if (in == null) {
			in = new NumberInput(textRenderer, min, max, set);
			inputs.put(id, in);
			addSelectableChild(in.widget());
		}
		in.setSetter(set);
		boolean hov = ui.over(r[0], r[1], r[2], Ui.H);
		ui.inset(r[0], r[1], r[2], Ui.H, 8, hov ? 1f : 0f);
		ui.text(ui.fit(label, r[2] - 76), r[0] + 8, r[1] + 8, Theme.TEXT);
		in.draw(ui, r[0] + r[2] - 64, r[1] + 4, 56, Ui.H - 8, current);
	}

	public void open(Screen next) {
		client.setScreen(next);
	}

	@Override
	protected void init() {
		ui.reset();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !UpdateDialog.isOpen();
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (UpdateDialog.isOpen()) return true;
		scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget - (int) (verticalAmount * 28)));
		return true;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		if (client.world == null) Brand.backdrop(ctx, width, height, System.currentTimeMillis(), 1.0f);
		RayConfig cfg = RayConfig.get();
		// fits the window whatever the GUI Scale is; follows the Menu Size setting
		float sc = UiScale.fit(width, height, 500, 340, cfg.menuScale);
		int viewW = UiScale.view(width, sc), viewH = UiScale.view(height, sc);
		int mx = UiScale.mouse(mouseX, sc), my = UiScale.mouse(mouseY, sc);
		long now = System.currentTimeMillis();
		float t = cfg.uiAnimations ? Math.min(1f, (now - openedAt) / 220f) : 1f;
		float open = Theme.easeOutCubic(t);
		if (client.world != null) ctx.fill(0, 0, width, height, Theme.fade(0xFF000000, 0.40f * open));
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(sc, sc);
		ui.begin(ctx, textRenderer, mx, my);
		int accent = Theme.accent();

		scroll = Math.round(scrollCur);
		scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-ui.dt * 16f));
		if (Math.abs(scrollTarget - scrollCur) < 0.5f) scrollCur = scrollTarget;

		int pw = Math.min(500, viewW - 8);
		int ph = Math.min(340, viewH - 8);
		int px = (viewW - pw) / 2, py = (viewH - ph) / 2 + Math.round((1f - open) * 16f);
		ui.surface(px, py, pw, ph, 12);

		// header: back + title (+ reset)
		boolean backHov = ui.over(px + 12, py + 11, 54, 22);
		float bv = ui.anim("backbtn", backHov);
		ui.rrect(px + 12, py + 11, 54, 22, 11, Theme.mix(Ui.white(0.06f), Ui.white(0.12f), bv));
		ui.rimGlass(px + 12, py + 11, 54, 22, 11, 0.24f + 0.16f * bv, 0.07f);
		ui.backIcon(px + 20 - Math.round(bv * 2f), py + 18, Theme.mix(Theme.DIM, 0xFFFFFFFF, bv));
		ui.text("Back", px + 31, py + 18, Theme.mix(Theme.DIM, 0xFFFFFFFF, bv));
		ui.click("back", px + 12, py + 11, 54, 22, this::close);
		ui.headText(heading, px + 78, py + 17, 0xFFFFFFFF);
		ui.gradBar(px + 78, py + 30, Math.min(40, ui.headWidth(heading)), 2, accent, Theme.accent2());
		if (hasReset()) {
			ui.labelButton(px + pw - 86, py + 11, 74, 22, "Reset", Theme.BAD, this::onReset);
		}
		ui.rect(px + 12, py + 40, pw - 24, 1, Ui.white(0.09f));

		// scrolling body
		int vx = px + 16, vy = py + 48, vw = pw - 40, vh = ph - 48 - 26;
		ui.pushClip(vx - 3, vy, vw + 6, vh);
		Ui.Form f = new Ui.Form(ui, vx, vy - scroll, vw);
		rows(ui, f);
		int contentH = f.bottom() - (vy - scroll);
		ui.popClip();
		scrollMax = Math.max(0, contentH - vh);
		scrollTarget = Math.max(0, Math.min(scrollMax, scrollTarget));
		scroll = Math.max(0, Math.min(scrollMax, scroll));
		ui.scrollbar(px + pw - 16, vy, vh, vh, contentH, scroll);
		viewTop = vy;
		viewBottom = vy + vh;

		ui.right("ESC to go back", px + pw - 16, py + ph - 18, Theme.MUTE);
		afterFrame();
		UpdateDialog.render(ui, viewW, viewH);
		ui.poll();
		if (ui.leftPressed && !inputs.isEmpty()) {
			NumberInput hit = null;
			for (NumberInput n : inputs.values()) {
				if (!UpdateDialog.isOpen() && my >= viewTop && my < viewBottom && n.contains(mx, my)) hit = n;
			}
			setFocused(hit == null ? null : hit.widget());
		}
		ctx.getMatrices().popMatrix();
	}

	@Override
	public void close() {
		RayConfig.save();
		client.setScreen(parent);
	}
}
