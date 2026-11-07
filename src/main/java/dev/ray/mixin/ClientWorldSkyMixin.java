package dev.ray.mixin;

import dev.ray.Sky;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom Sky: recolours the clouds (the sky disc itself is handled by SkyRenderingMixin). Different Minecraft versions return these colours either as a packed
 * int or as a Vec3d, so the hook looks at what it was actually given and only touches a type it understands. If a
 * method does not exist in this version, Mixin skips the hook (require = 0) and the vanilla sky simply stays.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldSkyMixin {
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true, require = 0)
	private void ray$clouds(float tickProgress, CallbackInfoReturnable cir) {
		if (!Sky.active((ClientWorld) (Object) this)) return;
		Object v = cir.getReturnValue();
		if (v instanceof Integer i) cir.setReturnValue(Sky.clouds(i));
		else if (v instanceof Vec3d vec) cir.setReturnValue(Sky.clouds(vec));
	}
}
