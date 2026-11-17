package dev.ray.mixin;

import dev.ray.HudLayers;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.21.5 and older have no per-element HUD registry, so the two vanilla HUD parts the client replaces are
 * switched off / moved here instead: the crosshair (Ray draws its own from HudLayers) and the sidebar scoreboard.
 */
@Mixin(InGameHud.class)
public class InGameHudLegacyMixin {
	@Unique
	private boolean ray$scoreboardPushed;

	/** Only a callback argument: the crosshair method's other arguments differ between versions. */
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
	private void ray$crosshair(CallbackInfo ci) {
		if (HudLayers.customCrosshair()) ci.cancel();
	}

	@Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
		at = @At("HEAD"), cancellable = true, require = 0)
	private void ray$scoreboardHead(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
		if (HudLayers.scoreboardHidden()) {
			ci.cancel();
			return;
		}
		ray$scoreboardPushed = HudLayers.scoreboardBegin(context);
	}

	@Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
		at = @At("RETURN"), require = 0)
	private void ray$scoreboardReturn(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
		HudLayers.scoreboardEnd(context, ray$scoreboardPushed);
		ray$scoreboardPushed = false;
	}
}
