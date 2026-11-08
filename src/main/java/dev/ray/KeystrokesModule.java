package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public class KeystrokesModule extends HudModule {
	public KeystrokesModule() {
		super("keystrokes", "Keystrokes", "Player", true, ML, 4, -36);
	}

	@Override
	public boolean prepare(MinecraftClient mc, TextRenderer tr, boolean editing) {
		w = 64;
		h = 42 + (opt("mouse") == 1 ? 22 : 0) + (opt("space") == 1 ? 16 : 0);
		return true;
	}

	@Override
	public void draw(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		int size = 20, gap = 2;
		int col1 = x + size + gap;
		int col2 = col1 + size + gap;
		key(ctx, tr, "W", col1, y, size, size, mc.options.forwardKey.isPressed());
		int row2 = y + size + gap;
		key(ctx, tr, "A", x, row2, size, size, mc.options.leftKey.isPressed());
		key(ctx, tr, "S", col1, row2, size, size, mc.options.backKey.isPressed());
		key(ctx, tr, "D", col2, row2, size, size, mc.options.rightKey.isPressed());
		int next = row2 + size + gap;
		if (opt("mouse") == 1) {
			int halfW = (w - gap) / 2;
			key(ctx, tr, "LMB " + CpsTracker.left(), x, next, halfW, size, CpsTracker.leftHeld());
			key(ctx, tr, "RMB " + CpsTracker.right(), x + halfW + gap, next, halfW, size, CpsTracker.rightHeld());
			next += size + gap;
		}
		if (opt("space") == 1) key(ctx, tr, "SPACE", x, next, w, 14, mc.options.jumpKey.isPressed());
	}

	private void key(DrawContext ctx, TextRenderer tr, String label, int kx, int ky, int kw, int kh, boolean down) {
		int idle = hasBg() ? bgColor() : 0x33000000;
		ctx.fill(kx, ky, kx + kw, ky + kh, down ? Theme.withAlpha(accentColor(), 0xCC) : idle);
		ClientFont.draw(ctx, tr, label, kx + (kw - ClientFont.width(tr, label)) / 2, ky + (kh - 8) / 2, textColor(), false);
	}
}
