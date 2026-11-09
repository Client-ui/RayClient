package dev.ray;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ArmorModule extends HudModule {
	private final List<ItemStack> stacks = new ArrayList<>();
	private boolean placeholder, showDur;

	public ArmorModule() {
		super("armor", "Armor HUD", "Player", true, MR, 4, -45);
	}

	@Override
	public boolean prepare(MinecraftClient mc, TextRenderer tr, boolean editing) {
		stacks.clear();
		showDur = opt("dur") == 1;
		boolean hand = opt("hand") == 1;
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
		for (EquipmentSlot s : slots) {
			if (s == EquipmentSlot.MAINHAND && !hand) continue;
			ItemStack st = mc.player.getEquippedStack(s);
			if (!st.isEmpty()) stacks.add(st);
		}
		placeholder = stacks.isEmpty();
		if (placeholder) {
			if (!editing) return false;
			w = ClientFont.width(tr, "Armor HUD") + 8;
			h = 12;
			return true;
		}
		int maxText = 0;
		if (showDur) {
			for (ItemStack st : stacks) {
				if (st.isDamageable()) maxText = Math.max(maxText, ClientFont.width(tr, String.valueOf(st.getMaxDamage() - st.getDamage())));
			}
		}
		w = 22 + maxText + 4;
		h = stacks.size() * 18;
		return true;
	}

	@Override
	public void draw(DrawContext ctx, MinecraftClient mc, TextRenderer tr) {
		drawBg(ctx, x, y, w, h);
		if (placeholder) {
			drawText(ctx, tr, "Armor HUD", x + 4, y + 2, textColor());
			return;
		}
		int yy = y;
		for (ItemStack st : stacks) {
			ctx.drawItem(st, x + 1, yy + 1);
			ctx.drawStackOverlay(tr, st, x + 1, yy + 1);
			if (showDur && st.isDamageable()) {
				String d = String.valueOf(st.getMaxDamage() - st.getDamage());
				drawText(ctx, tr, d, x + 22, yy + 5, textColor());
			}
			yy += 18;
		}
	}
}
