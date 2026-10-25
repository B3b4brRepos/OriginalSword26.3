package com.originalsword;

import com.mojang.blaze3d.vertex.PoseStack;
import com.originalsword.bedrock.BonePose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.Optional;

/** Draws the Bedrock hands + sword in place of vanilla's first-person item rendering. */
public final class SwordRenderer {
	private static final Item[] SWORDS = {
			Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
			Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
	private static final String[] TEX = {"wooden", "stone", "iron", "golden", "diamond", "netherite"};
	private static final int FULL_BRIGHT = 15728880;

	private static SwordAssets assets;
	private static SwordAnimator animator;
	private static long lastNanos = 0, lastCfgCheck = 0;
	private static boolean lastAttackDown = false;
	private static java.lang.reflect.Method isPausedMethod;
	private static boolean pausedLookedUp;

	private SwordRenderer() {}

	public static void init(SwordAssets a) {
		assets = a;
		animator = new SwordAnimator(a, SwordRenderer::playSound);
	}

	private static int itemCode(ItemStack s) {
		for (int i = 0; i < SWORDS.length; i++) if (s.is(SWORDS[i])) return i + 1;
		return 0;
	}

	private static void playSound(String effect) {
		if (!Config.current.playSounds) return;
		String name = switch (effect) {
			case "sword_swing1" -> "swing1";
			case "sword_swing2" -> "swing2";
			case "sword_swing3" -> "swing3";
			case "sword_strong" -> "strong";
			case "sword_draw" -> "draw";
			case "sword_put_away" -> "put_away";
			case "sword_inspect" -> "inspect";
			default -> null;
		};
		if (name == null) return;
		SoundEvent ev = new SoundEvent(Identifier.fromNamespaceAndPath(OriginalSwordClient.MOD_ID, "sword." + name), Optional.empty());
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, 1.0F, 1.0F));
	}

	/** Looked up reflectively because pause handling moved around in 26.2/26.3. */
	private static boolean isPaused() {
		try {
			if (!pausedLookedUp) {
				pausedLookedUp = true;
				try { isPausedMethod = Minecraft.class.getMethod("isPaused"); } catch (NoSuchMethodException ignored) {}
			}
			return isPausedMethod != null && (Boolean) isPausedMethod.invoke(Minecraft.getInstance());
		} catch (Throwable t) {
			return false;
		}
	}

	/** @return true if we drew the hands/sword and vanilla rendering should be skipped */
	public static boolean render(PoseStack ps, SubmitNodeCollector collector, LocalPlayer player, int light) {
		if (assets == null) return false;
		long now = System.nanoTime();
		if (now - lastCfgCheck > 1_000_000_000L) { Config.reloadIfChanged(); lastCfgCheck = now; }
		Config cfg = Config.current;

		// A click = attack key pressed this frame (independent of vanilla swing fields, which changed in 26.3).
		boolean down = Minecraft.getInstance().options.keyAttack.isDown();
		boolean edge = down && !lastAttackDown;
		lastAttackDown = down;
		boolean swinging = down;

		double dt = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1e9, 0.1);
		lastNanos = now;
		if (isPaused()) dt = 0;

		int item = cfg.enabled ? itemCode(player.getMainHandItem()) : 0;
		boolean quiet = player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4
				&& !player.isUsingItem() && !swinging;
		animator.update(dt, item, edge, quiet);
		if (item == 0) return false;

		Map<String, BonePose> pose = animator.pose();
		Map<String, Matrix4f> swordMats = assets.sword.computeMatrices(pose, cfg.rotSign, cfg.staticRotSign, cfg.posSign);
		Map<String, Matrix4f> armMats = assets.arms.computeMatrices(pose, cfg.rotSign, cfg.staticRotSign, cfg.posSign);

		Identifier swordTex = Identifier.fromNamespaceAndPath(OriginalSwordClient.MOD_ID,
				"textures/sword/sword_" + TEX[item - 1] + ".png");
		float[] rs = cfg.staticRotSign;

		ps.pushPose();
		float s = cfg.scale / 16f;
		boolean mirror = cfg.mirrorX ^ (player.getMainArm() == HumanoidArm.LEFT);
		ps.scale(mirror ? -s : s, s, s);
		ps.translate(-cfg.camera[0] - cfg.offset[0], -cfg.camera[1] - cfg.offset[1], -cfg.camera[2] - cfg.offset[2]);

		collector.submitCustomGeometry(ps, RenderTypes.entityCutoutNoCull(swordTex),
				(p, vc) -> assets.sword.render(vc, p.pose(), swordMats, rs, light, b -> !b.emissive()));
		collector.submitCustomGeometry(ps, RenderTypes.entityTranslucent(swordTex),
				(p, vc) -> assets.sword.render(vc, p.pose(), swordMats, rs, FULL_BRIGHT, b -> b.emissive()));
		if (cfg.showArms) {
			Identifier skin = player.getSkin().body().texturePath();
			collector.submitCustomGeometry(ps, RenderTypes.entityCutoutNoCull(skin),
					(p, vc) -> assets.arms.render(vc, p.pose(), armMats, rs, light, b -> true));
		}
		ps.popPose();
		return true;
	}
}
