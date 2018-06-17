package acmi.l2.clientmod.decompiler.bytecode;

import acmi.l2.clientmod.unreal.UnrealRuntimeContext;

interface BytecodeEntry {
    <T extends Appendable> T appendTo(T sb, UnrealRuntimeContext context, int indent);
}
