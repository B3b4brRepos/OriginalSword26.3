package com.originalsword;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;

/** Called from the ASM-injected prologue of FirstPersonHandsAndItemsRenderer#submitHandsWithItems. */
public final class FirstPersonHook {
	private static boolean failed;

	private FirstPersonHook() {}

	/** @return true to skip vanilla first-person hand/item rendering */
	public static boolean intercept(Object[] args) {
		if (failed) return false;
		try {
			PoseStack ps = null;
			SubmitNodeCollector collector = null;
			int light = 15728880;
			boolean lightSet = false;
			for (Object o : args) {
				if (ps == null && o instanceof PoseStack p) ps = p;
				else if (collector == null && o instanceof SubmitNodeCollector c) collector = c;
				else if (!lightSet && o instanceof Integer i) { light = i; lightSet = true; }
			}
			LocalPlayer player = Minecraft.getInstance().player;
			if (ps == null || collector == null || player == null) return false;
			return SwordRenderer.render(ps, collector, player, light);
		} catch (Throwable t) {
			failed = true; // never crash the render loop; fall back to vanilla hands
			OriginalSwordClient.LOGGER.error("OriginalSword render failed, restoring vanilla hands", t);
			return false;
		}
	}
}
