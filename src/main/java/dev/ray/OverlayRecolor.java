package dev.ray;

/** Implemented by the OverlayTexture mixin so the rest of the client can recolour the hurt flash without touching mixin classes. */
public interface OverlayRecolor {
	void ray$recolor(int argb);
}
