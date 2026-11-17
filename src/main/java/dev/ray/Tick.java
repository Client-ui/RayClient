package dev.ray;

/** The partial-tick progress of the frame being drawn. Minecraft's own type for this changes between versions, so the client uses this. */
public record Tick(float progress) {
}
