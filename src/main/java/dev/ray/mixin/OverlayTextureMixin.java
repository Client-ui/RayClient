package dev.ray.mixin;

import dev.ray.HitColor;
import dev.ray.OverlayRecolor;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets Hit Color rewrite the red "hurt" half of the entity overlay texture. */
@Mixin(OverlayTexture.class)
public class OverlayTextureMixin implements OverlayRecolor {
	@Shadow
	@Final
	private NativeImageBackedTexture texture;

	@Inject(method = "<init>", at = @At("TAIL"), require = 0)
	private void ray$captured(CallbackInfo ci) {
		HitColor.overlay = this;
	}

	@Override
	public void ray$recolor(int argb) {
		NativeImage image = texture.getImage();
		if (image == null) return;
		// the top 8 rows of the 16x16 texture are the hurt flash; the bottom rows are the white "creeper flash"
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) image.setColorArgb(x, y, argb);
		}
		texture.upload();
	}
}
