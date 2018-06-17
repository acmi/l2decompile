package acmi.l2.clientmod.decompiler.bytecode;

import java.io.IOException;
import java.util.NavigableMap;
import java.util.TreeMap;

import acmi.l2.clientmod.unreal.UnrealRuntimeContext;
import acmi.l2.clientmod.unreal.bytecode.token.JumpIfNot;
import acmi.l2.clientmod.unreal.bytecode.token.Token;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import static acmi.l2.clientmod.decompiler.Util.newLine;
import static acmi.l2.clientmod.decompiler.bytecode.ByteCodeDecompiler.decompile;

@RequiredArgsConstructor
@Getter
class DoUntil implements Loop {
    private final Token condition;
    private final NavigableMap<Integer, BytecodeEntry> body;

    @SneakyThrows(IOException.class)
    @Override
    public <T extends Appendable> T appendTo(T sb, UnrealRuntimeContext context, int indent) {
        sb.append("do");
        sb.append(newLine(indent));
        sb.append("{");
        for (BytecodeEntry e: body.values()) {
            sb.append(newLine(indent + 1));
            e.appendTo(sb, context, indent + 1);
        }
        sb.append(newLine(indent)).append("}");
        sb.append(newLine(indent)).append("until ( ");
        sb.append(condition.toString(context));
        sb.append(" );");
        return sb;
    }

    static Decompiler decompiler = (i, t, code)->{
        if (t instanceof BEToken && ((BEToken) t).getToken() instanceof JumpIfNot) {
            JumpIfNot jin = (JumpIfNot) ((BEToken) t).getToken();
            if (jin.targetOffset < i) {
                NavigableMap<Integer, BytecodeEntry> sub = new TreeMap<>(code.subMap(jin.targetOffset, true, i, false));
                sub.keySet().forEach(code::remove);
                code.remove(i);
                code.put(jin.targetOffset, new DoUntil(jin.condition, decompile(sub)));
            }
        }
    };
}
