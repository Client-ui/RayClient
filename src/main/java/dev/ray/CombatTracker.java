package dev.ray;
import net.minecraft.sound.SoundCategory;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/** Tracks combo, reach, hit markers and pearl cooldown for the PvP HUD modules (display only). */
public final class CombatTracker {
	public static int combo = 0;
	public static double reach = -1;
	private static long lastHit = 0;
	private static long pearlUntil = 0;
	private static boolean wasHurt = false;
	private static float lastHealth = -1;

	private CombatTracker() {}

	public static void init() {
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (player == mc.player && entity instanceof LivingEntity) {
				combo++;
				lastHit = System.currentTimeMillis();
				reach = hit != null ? hit.getPos().distanceTo(player.getEyePos()) : player.distanceTo(entity);
				if (RayConfig.get().hitSound) playHitSound(mc);
			}
			return ActionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			if (player == mc.player && player.getStackInHand(hand).isOf(Items.ENDER_PEARL)
				&& System.currentTimeMillis() >= pearlUntil) {
				pearlUntil = System.currentTimeMillis() + 1000L;
			}
			return ActionResult.PASS;
		});
	}

	/** Plays the hit sound: your own file if one is chosen, otherwise the Minecraft sound. Also used by the Test button. */
	public static void playHitSound(MinecraftClient mc) {
		RayConfig c = RayConfig.get();
		if (c.hitSoundCustom && CustomSounds.play(c.hitSoundFile, c.hitSoundVolume / 100.0f, c.hitSoundFilePitch / 100.0f)) return;
		mc.getSoundManager().play(new PositionedSoundInstance(
			SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
			SoundCategory.PLAYERS,
			c.hitSoundVolume / 100.0f,
			c.hitSoundPitch / 100.0f,
			Random.create(),
			mc.player != null ? mc.player.getBlockPos() : BlockPos.ORIGIN
		));
	}

	public static long lastHitMs() { return lastHit; }

	public static long pearlRemainingMs() { return Math.max(0L, pearlUntil - System.currentTimeMillis()); }

	public static void tick(MinecraftClient mc) {
		if (mc.player == null) return;
		boolean hurt = mc.player.hurtTime > 0;
		if (hurt && !wasHurt) combo = 0;
		wasHurt = hurt;
		float hp = mc.player.getHealth();
		if (lastHealth >= 0 && hp < lastHealth) combo = 0;
		lastHealth = hp;
		if (combo > 0 && System.currentTimeMillis() - lastHit > 4000L) combo = 0;
	}
}
