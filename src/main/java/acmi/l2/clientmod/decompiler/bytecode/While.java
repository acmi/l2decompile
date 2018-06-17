package acmi.l2.clientmod.decompiler.bytecode;

import java.io.IOException;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import acmi.l2.clientmod.unreal.UnrealRuntimeContext;
import acmi.l2.clientmod.unreal.bytecode.token.Jump;
import acmi.l2.clientmod.unreal.bytecode.token.JumpIfNot;
import acmi.l2.clientmod.unreal.bytecode.token.Token;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import static acmi.l2.clientmod.decompiler.Util.newLine;
import static acmi.l2.clientmod.decompiler.bytecode.ByteCodeDecompiler.decompile;

@RequiredArgsConstructor
@Getter
class While implements Loop {
    private final Token condition;
    private final NavigableMap<Integer, BytecodeEntry> body;

    @SneakyThrows(IOException.class)
    @Override
    public <T extends Appendable> T appendTo(T sb, UnrealRuntimeContext context, int indent) {
        sb.append("while ( ");
        sb.append(condition.toString(context));
        sb.append(" )");
        sb.append(newLine(indent)).append("{");
        for (BytecodeEntry e: body.values()) {
            sb.append(newLine(indent + 1));
            e.appendTo(sb, context, indent + 1);
        }
        sb.append(newLine(indent)).append("}");
        return sb;
    }

    static Decompiler decompiler = (i, t, code) -> {
        if (t instanceof BEToken && ((BEToken) t).getToken() instanceof JumpIfNot) {
            JumpIfNot jin = (JumpIfNot) ((BEToken) t).getToken();
            Map.Entry<Integer, BytecodeEntry> beforeDest = code.lowerEntry(jin.targetOffset);
            if (beforeDest.getValue() instanceof BEToken && ((BEToken) beforeDest.getValue()).getToken() instanceof Jump) {
                Jump j = (Jump) ((BEToken) beforeDest.getValue()).getToken();
                if (j.targetOffset == i) {
                    NavigableMap<Integer, BytecodeEntry> sub = new TreeMap<>(code.subMap(i, false, beforeDest.getKey(), false));
                    sub.keySet().forEach(code::remove);
                    code.remove(beforeDest.getKey());
                    code.put(i, new While(jin.condition, decompile(sub)));
                }
            }
        }
    };
}
