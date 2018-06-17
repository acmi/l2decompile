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
class If implements BytecodeEntry {
    private final Token condition;
    private final NavigableMap<Integer, BytecodeEntry> body;
    private final NavigableMap<Integer, BytecodeEntry> elseBody;

    @SneakyThrows(IOException.class)
    @Override
    public <T extends Appendable> T appendTo(T sb, UnrealRuntimeContext context, int indent) {
//            boolean f = (indent & 0x100) > 0;
//            indent = indent & 0xFF;
        sb.append("if ( ").append(condition.toString(context)).append(" )");
//            if (f) {
//                sb.append(" ");
//            } else {
        sb.append(newLine(indent));
//            }
        sb.append("{");
        for (BytecodeEntry e: body.values()) {
            sb.append(newLine(indent + 1));
            e.appendTo(sb, context, indent + 1);
        }
        sb.append(newLine(indent)).append("}");
        if (elseBody != null && !elseBody.isEmpty()) {
            if (elseBody.get(0) instanceof If) {
                sb.append(newLine(indent)).append("else ");
                elseBody.get(0).appendTo(sb, context, indent/* | 0x100*/);
            } else {
                sb.append(newLine(indent)).append("else").append(newLine(indent)).append("{");
                for (BytecodeEntry e: elseBody.values()) {
                    sb.append(newLine(indent + 1));
                    e.appendTo(sb, context, indent + 1);
                }
                sb.append(newLine(indent)).append("}");
            }
        }
        return sb;
    }

    static Decompiler decompilerIfElse = (i, t, code)->{
        if (t instanceof BEToken && ((BEToken) t).getToken() instanceof JumpIfNot) {
            JumpIfNot jin = (JumpIfNot) ((BEToken) t).getToken();
            Map.Entry<Integer, BytecodeEntry> beforeDest = code.lowerEntry(jin.targetOffset);
            if (beforeDest.getValue() instanceof BEToken && ((BEToken) beforeDest.getValue()).getToken() instanceof Jump) {
                Jump j = (Jump) ((BEToken) beforeDest.getValue()).getToken();
                if (j.targetOffset > jin.targetOffset) {
                    NavigableMap<Integer, BytecodeEntry> sub1 = new TreeMap<>(code.subMap(code.higherKey(i), true, beforeDest.getKey(), false));
                    NavigableMap<Integer, BytecodeEntry> sub2 = new TreeMap<>(code.subMap(jin.targetOffset, true, j.targetOffset, false));
                    sub1.keySet().forEach(code::remove);
                    code.remove(beforeDest.getKey());
                    sub2.keySet().forEach(code::remove);
                    code.put(i, new If(jin.condition, decompile(sub1), decompile(sub2)));
                }
            }
        }
    };

    static Decompiler decompilerIf = (i, t, code)->{
        if (t instanceof BEToken && ((BEToken) t).getToken() instanceof JumpIfNot) {
            JumpIfNot jin = (JumpIfNot) ((BEToken) t).getToken();
            NavigableMap<Integer, BytecodeEntry> sub = new TreeMap<>(code.subMap(i, false, jin.targetOffset, false));
            sub.keySet().forEach(code::remove);
            code.put(i, new If(jin.condition, decompile(sub), null));
        }
    };
}
