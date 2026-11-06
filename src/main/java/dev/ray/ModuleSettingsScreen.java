package dev.ray;

import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Per-module settings: scale, colours, background, shadow, border, toggle key and module-specific options. */
public class ModuleSettingsScreen extends SettingsScreen {
	private static final String[] SHADOW = {"Default", "On", "Off"};
	private static final String[] VISIBILITY = {"Always", "Hide while chatting", "Only holding Tab"};

	private final HudModule m;
	private boolean listening = false;
	private long listenStart = 0;

	public ModuleSettingsScreen(Screen parent, HudModule m) {
		super(parent, m.name + " Settings");
		this.m = m;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return !listening; // while waiting for a key, ESC cancels the prompt instead of closing the page
	}

	@Override
	protected boolean hasReset() {
		return true;
	}

	@Override
	protected void onReset() {
		RayConfig.get().styles.remove(m.id);
		RayConfig.save();
	}

	@Override
	protected void rows(Ui ui, Ui.Form f) {
		RayConfig.Style st = m.style();

		f.section("General");
		ui.toggle(f.half(), "Enabled", m.enabled(), () -> m.setEnabled(!m.enabled()));
		ui.slider(f.half(), "Scale", 50, 200, st.scale, "%", v -> st.scale = v);

		f.section("Look");
		ui.colorChoice(f.half(), "Text Color", Theme.PALETTE_NAMES[st.textColor], Theme.pick(st.textColor, 0xFFFFFFFF),
			() -> st.textColor = (st.textColor + 1) % Theme.PALETTE_NAMES.length);
		ui.colorChoice(f.half(), "Accent Color", Theme.PALETTE_NAMES[st.accentColor], Theme.pick(st.accentColor, Theme.accent()),
			() -> st.accentColor = (st.accentColor + 1) % Theme.PALETTE_NAMES.length);
		ui.sliderShown(f.half(), "Background", -1, 100, st.bg, st.bg < 0 ? "Global" : st.bg + "%", v -> st.bg = v);
		ui.toggle(f.half(), "Accent Bar", st.accentBar, () -> st.accentBar = !st.accentBar);
		final int shIdx = st.shadow < 0 ? 0 : (st.shadow == 1 ? 1 : 2);
		ui.choice(f.half(), "Text Shadow", SHADOW[shIdx], () -> {
			int n = (shIdx + 1) % 3;
			st.shadow = n == 0 ? -1 : (n == 1 ? 1 : 0);
		});
		ui.toggle(f.half(), "Border", st.border, () -> st.border = !st.border);

		ui.slider(f.half(), "Opacity", 20, 100, st.opacity, "%", v -> st.opacity = v);
		ui.choice(f.half(), "Show When", VISIBILITY[Math.max(0, Math.min(2, st.visibility))], () -> st.visibility = (st.visibility + 1) % 3);

		f.section("Controls");
		ui.choice(f.half(), "Toggle Key", listening ? "Press a key..." : Keys.name(st.key), () -> {
			listening = true;
			listenStart = System.currentTimeMillis();
		});

		if (m.id.equals("scoreboard")) {
			RayConfig cfg = RayConfig.get();
			f.section("Scoreboard");
			ui.toggle(f.half(), "Hide Scoreboard", cfg.hideScoreboard, () -> cfg.hideScoreboard = !cfg.hideScoreboard);
			ui.slider(f.half(), "Scoreboard Scale", 50, 150, cfg.scoreboardScale, "%", v -> cfg.scoreboardScale = v);
		}

		List<Modules.Opt> opts = Modules.optionsFor(m.id);
		if (!opts.isEmpty()) f.section("Options");
		for (Modules.Opt o : opts) {
			final int v = Modules.opt(m.id, o.key);
			if (o.type == Modules.Opt.TOGGLE) {
				ui.toggle(f.half(), o.label, v != 0, () -> st.opts.put(o.key, v == 0 ? 1 : 0));
			} else if (o.type == Modules.Opt.SLIDER) {
				ui.slider(f.half(), o.label, o.min, o.max, v, val -> st.opts.put(o.key, val));
			} else {
				ui.choice(f.half(), o.label, o.choices[Math.max(0, Math.min(o.choices.length - 1, v))],
					() -> st.opts.put(o.key, (v + 1) % o.choices.length));
			}
		}
	}

	@Override
	protected void afterFrame() {
		if (!listening || System.currentTimeMillis() - listenStart <= 250L) return;
		long kw = client.getWindow().getHandle();
		for (int k = GLFW.GLFW_KEY_SPACE; k <= GLFW.GLFW_KEY_LAST; k++) {
			if (GLFW.glfwGetKey(kw, k) == GLFW.GLFW_PRESS) {
				listening = false;
				if (k != GLFW.GLFW_KEY_ESCAPE) {
					m.style().key = (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) ? -1 : k;
					RayConfig.save();
				}
				break;
			}
		}
	}
}
