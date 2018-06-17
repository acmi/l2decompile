package acmi.l2.clientmod.decompiler.bytecode;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import acmi.l2.clientmod.unreal.UnrealRuntimeContext;
import acmi.l2.clientmod.unreal.bytecode.token.Assert;
import acmi.l2.clientmod.unreal.bytecode.token.ClassContext;
import acmi.l2.clientmod.unreal.bytecode.token.Context;
import acmi.l2.clientmod.unreal.bytecode.token.DynArraySort;
import acmi.l2.clientmod.unreal.bytecode.token.FinalFunction;
import acmi.l2.clientmod.unreal.bytecode.token.GlobalFunction;
import acmi.l2.clientmod.unreal.bytecode.token.GotoLabel;
import acmi.l2.clientmod.unreal.bytecode.token.Insert;
import acmi.l2.clientmod.unreal.bytecode.token.IteratorNext;
import acmi.l2.clientmod.unreal.bytecode.token.Let;
import acmi.l2.clientmod.unreal.bytecode.token.LetBool;
import acmi.l2.clientmod.unreal.bytecode.token.NativeFunctionCall;
import acmi.l2.clientmod.unreal.bytecode.token.Remove;
import acmi.l2.clientmod.unreal.bytecode.token.Return;
import acmi.l2.clientmod.unreal.bytecode.token.Token;
import acmi.l2.clientmod.unreal.bytecode.token.VirtualFunction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
@Getter
class BEToken implements BytecodeEntry {

    private final Token token;

    @SneakyThrows(IOException.class)
    @Override
    public <T extends Appendable> T appendTo(T sb, UnrealRuntimeContext context, int indent) {
        sb.append(token.toString(context));
        sb.append(";");
        return sb;
    }

    @Override
    public String toString() {
        return token.toString();
    }

}
