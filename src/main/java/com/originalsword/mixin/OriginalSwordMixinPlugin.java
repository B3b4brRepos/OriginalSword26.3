package com.originalsword.mixin;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Signature-agnostic hook. At the start of FirstPersonHandsAndItemsRenderer#submitHandsWithItems it inserts:
 *
 *   if (FirstPersonHook.intercept(new Object[]{ all arguments, boxed })) return;
 *
 * so it works whatever parameters 26.3 gives that method. If the method is not found it logs and does nothing,
 * leaving vanilla hands untouched instead of crashing.
 */
public final class OriginalSwordMixinPlugin implements IMixinConfigPlugin {
	private static final String HOOK = "com/originalsword/FirstPersonHook";
	private static final String[] METHOD_NAMES = {"submitHandsWithItems", "renderHandsWithItems"};

	@Override public void onLoad(String mixinPackage) {}
	@Override public String getRefMapperConfig() { return null; }
	@Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
	@Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
	@Override public List<String> getMixins() { return null; }
	@Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

	@Override
	public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
		MethodNode best = null;
		for (MethodNode m : node.methods) {
			boolean named = false;
			for (String n : METHOD_NAMES) if (m.name.equals(n)) named = true;
			if (!named || (m.access & Opcodes.ACC_STATIC) != 0) continue;
			if (Type.getReturnType(m.desc).getSort() != Type.VOID) continue;
			// prefer the overload that takes a PoseStack (the real one)
			if (best == null || m.desc.contains("PoseStack")) best = m;
		}
		if (best == null) {
			System.err.println("[OriginalSword] Could not find submitHandsWithItems in " + target
					+ " - first-person sword animations disabled (vanilla hands kept).");
			return;
		}

		Type[] args = Type.getArgumentTypes(best.desc);
		InsnList il = new InsnList();
		pushInt(il, args.length);
		il.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
		int slot = 1; // slot 0 = this
		for (int i = 0; i < args.length; i++) {
			Type t = args[i];
			il.add(new InsnNode(Opcodes.DUP));
			pushInt(il, i);
			il.add(new VarInsnNode(t.getOpcode(Opcodes.ILOAD), slot));
			box(il, t);
			il.add(new InsnNode(Opcodes.AASTORE));
			slot += t.getSize();
		}
		il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "intercept", "([Ljava/lang/Object;)Z", false));
		LabelNode cont = new LabelNode();
		il.add(new JumpInsnNode(Opcodes.IFEQ, cont));
		il.add(new InsnNode(Opcodes.RETURN));
		il.add(cont);

		AbstractInsnNode first = best.instructions.getFirst();
		if (first == null) best.instructions.add(il); else best.instructions.insertBefore(first, il);
		best.maxStack = Math.max(best.maxStack, 6);
		System.out.println("[OriginalSword] Hooked " + target + "#" + best.name + best.desc);
	}

	private static void pushInt(InsnList il, int v) {
		if (v >= -1 && v <= 5) il.add(new InsnNode(Opcodes.ICONST_0 + v));
		else il.add(new IntInsnNode(Opcodes.BIPUSH, v));
	}

	private static void box(InsnList il, Type t) {
		String owner, desc;
		switch (t.getSort()) {
			case Type.BOOLEAN -> { owner = "java/lang/Boolean"; desc = "(Z)Ljava/lang/Boolean;"; }
			case Type.BYTE -> { owner = "java/lang/Byte"; desc = "(B)Ljava/lang/Byte;"; }
			case Type.CHAR -> { owner = "java/lang/Character"; desc = "(C)Ljava/lang/Character;"; }
			case Type.SHORT -> { owner = "java/lang/Short"; desc = "(S)Ljava/lang/Short;"; }
			case Type.INT -> { owner = "java/lang/Integer"; desc = "(I)Ljava/lang/Integer;"; }
			case Type.FLOAT -> { owner = "java/lang/Float"; desc = "(F)Ljava/lang/Float;"; }
			case Type.LONG -> { owner = "java/lang/Long"; desc = "(J)Ljava/lang/Long;"; }
			case Type.DOUBLE -> { owner = "java/lang/Double"; desc = "(D)Ljava/lang/Double;"; }
			default -> { return; }
		}
		il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "valueOf", desc, false));
	}
}
