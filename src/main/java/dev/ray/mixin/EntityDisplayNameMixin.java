package dev.ray.mixin;

import dev.ray.Nametags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts the Ray logo in front of the name of players who use the client (this name is what the nametag shows). */
@Mixin(Entity.class)
public class EntityDisplayNameMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
	private void ray$logo(CallbackInfoReturnable<Text> cir) {
		if (!((Object) this instanceof PlayerEntity player)) return;
		if (Nametags.uses(player.getUuid())) cir.setReturnValue(Nametags.decorate(cir.getReturnValue()));
	}
}
