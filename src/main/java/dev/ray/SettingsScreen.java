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
	private int scroll = 0, scrollMax = 0;
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
		ui.panel(r[0], r[1], r[2], Ui.H, 5, hov ? Theme.CARD_HOVER : Theme.CARD, Theme.STROKE);
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
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		scroll = Math.max(0, Math.min(scrollMax, scroll - (int) (verticalAmount * 20)));
		return true;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		if (client.world == null) Brand.backdrop(ctx, width, height, System.currentTimeMillis(), 1.0f);
		RayConfig cfg = RayConfig.get();
		// follows the Menu Size setting, like the main Ray menu
		float sc = cfg.menuScale / 100.0f;
		int viewW = Math.round(width / sc), viewH = Math.round(height / sc);
		int mx = Math.round(mouseX / sc), my = Math.round(mouseY / sc);
		float t = cfg.uiAnimations ? Math.min(1f, (System.currentTimeMillis() - openedAt) / 190f) : 1f;
		float open = 1f - (1f - t) * (1f - t) * (1f - t);
		if (client.world != null) ctx.fill(0, 0, width, height, Theme.fade(0xFF000000, 0.32f * open));
		ctx.getMatrices().pushMatrix();
		ctx.getMatrices().scale(sc, sc);
		ui.begin(ctx, textRenderer, mx, my);
		int accent = Theme.accent();

		int pw = Math.min(460, viewW - 16);
		int ph = Math.min(300, viewH - 16);
		int px = (viewW - pw) / 2, py = (viewH - ph) / 2 + Math.round((1f - open) * 14f);
		ui.shadow(px, py, pw, ph, 10);
		ui.glow(px, py, pw, ph, 10, accent, 0.3f);
		ui.panelGrad(px, py, pw, ph, 10, ui.glass(Theme.PANEL_TOP), ui.glass(Theme.PANEL), Theme.fade(accent, 0.5f));
		ui.rrect(px + 12, py + 1, pw - 24, 2, 1, Theme.fade(accent, 0.85f));

		// header: back + title (+ reset)
		boolean backHov = ui.over(px + 8, py + 9, 46, 17);
		ui.rrect(px + 8, py + 9, 46, 17, 8, backHov ? Theme.CARD_HOVER : Theme.CARD);
		ui.backIcon(px + 14, py + 14, Theme.DIM);
		ui.text("Back", px + 25, py + 14, Theme.TEXT);
		ui.click("back", px + 8, py + 9, 46, 17, this::close);
		ui.textShadow(heading, px + 62, py + 14, 0xFFFFFFFF);
		if (hasReset()) {
			ui.labelButton(px + pw - 78, py + 9, 70, 17, "Reset", 0xFF7F1D1D, this::onReset);
		}
		ui.rect(px + 8, py + 31, pw - 16, 1, Theme.STROKE);

		// scrolling body
		int vx = px + 12, vy = py + 38, vw = pw - 28, vh = ph - 38 - 22;
		ui.pushClip(vx - 2, vy, vw + 4, vh);
		Ui.Form f = new Ui.Form(ui, vx, vy - scroll, vw);
		rows(ui, f);
		int contentH = f.bottom() - (vy - scroll);
		ui.popClip();
		scrollMax = Math.max(0, contentH - vh);
		scroll = Math.max(0, Math.min(scrollMax, scroll));
		ui.scrollbar(px + pw - 9, vy, vh, vh, contentH, scroll);
		viewTop = vy;
		viewBottom = vy + vh;

		ui.right("ESC to go back", px + pw - 10, py + ph - 14, Theme.MUTE);
		afterFrame();
		ui.poll();
		if (ui.leftPressed && !inputs.isEmpty()) {
			NumberInput hit = null;
			for (NumberInput n : inputs.values()) {
				if (my >= viewTop && my < viewBottom && n.contains(mx, my)) hit = n;
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
