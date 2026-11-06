package dev.ray;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * A small box you can type a decimal number into (e.g. 1.33). The value is applied live while you type; when the box
 * is not focused it always shows the current value. The owning screen must register {@link #widget()} as a child
 * (addSelectableChild) so it receives keyboard input, and focus it on click.
 */
public final class NumberInput {
	private final TextFieldWidget field;
	private final float min, max;
	private Consumer<Float> setter;
	private boolean syncing = false;
	private int bx, by, bw, bh;

	public NumberInput(TextRenderer tr, float min, float max, Consumer<Float> setter) {
		this.min = min;
		this.max = max;
		this.setter = setter;
		field = new TextFieldWidget(tr, 0, 0, 40, 10, Text.literal("number"));
		field.setDrawsBackground(false);
		field.setMaxLength(6);
		field.setTextPredicate(s -> s.matches("[0-9]{0,2}([.,][0-9]{0,3})?"));
		field.setChangedListener(s -> {
			if (syncing) return;
			Float v = parse(s);
			if (v != null) this.setter.accept(Math.max(this.min, Math.min(this.max, v)));
		});
	}

	public TextFieldWidget widget() {
		return field;
	}

	public void setSetter(Consumer<Float> setter) {
		this.setter = setter;
	}

	public boolean contains(int x, int y) {
		return x >= bx && x < bx + bw && y >= by && y < by + bh;
	}

	public static Float parse(String s) {
		String t = s.replace(',', '.').trim();
		if (t.isEmpty() || t.equals(".")) return null;
		try {
			return Float.parseFloat(t);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** 1.0 -> "1.0", 1.33 -> "1.33" (always a dot, whatever the system language is). */
	public static String format(float v) {
		String s = String.format(Locale.ROOT, "%.2f", v);
		s = s.replaceAll("0+$", "");
		if (s.endsWith(".")) s += "0";
		return s;
	}

	/** Draws the box at the given position showing/editing the current value. */
	public void draw(Ui ui, int x, int y, int w, int h, float current) {
		bx = x;
		by = y;
		bw = w;
		bh = h;
		boolean focused = field.isFocused();
		ui.panel(x, y, w, h, 3, 0xFF0D1018, focused ? Theme.accent() : (ui.over(x, y, w, h) ? Theme.DIM : Theme.STROKE));
		if (!focused) {
			String t = format(current);
			if (!field.getText().equals(t)) {
				syncing = true;
				field.setText(t);
				syncing = false;
			}
		}
		field.setX(x + 5);
		field.setY(y + (h - 8) / 2);
		field.setWidth(w - 10);
		field.render(ui.ctx, ui.mx, ui.my, 0f);
	}
}
