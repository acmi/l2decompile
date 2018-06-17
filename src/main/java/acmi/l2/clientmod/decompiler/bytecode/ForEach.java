package acmi.l2.clientmod.decompiler.bytecode;

import java.io.IOException;
import java.util.NavigableMap;
import java.util.TreeMap;

import acmi.l2.clientmod.unreal.UnrealRuntimeContext;
import acmi.l2.clientmod.unreal.bytecode.token.Iterator;
import acmi.l2.clientmod.unreal.bytecode.token.Token;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import static acmi.l2.clientmod.decompiler.Util.newLine;
import static acmi.l2.clientmod.decompiler.bytecode.ByteCodeDecompiler.decompile;

@RequiredArgsConstructor
@Getter
class ForEach implements Loop {
    private final Token iterator;
    private final NavigableMap<Integer, BytecodeEntry> body;

    @SneakyThrows(IOException.class)
    @Override
    public <T extends Appendable> T appendTo(T sb, UnrealRuntimeContext context, int indent) {
        sb.append("ForEach ").append(iterator.toString(context));
        sb.append(newLine(indent)).append("{");
        for (BytecodeEntry e: body.values()) {
            sb.append(newLine(indent + 1));
            e.appendTo(sb, context, indent + 1);
        }
        sb.append(newLine(indent)).append("}");
        return sb;
    }

    static Decompiler decompiler = (i, t, code) -> {
        if (t instanceof BEToken && ((BEToken) t).getToken() instanceof Iterator) {
            Iterator iterator = (Iterator) ((BEToken) t).getToken();
            NavigableMap<Integer, BytecodeEntry> sub = new TreeMap<>(code.subMap(i, false, iterator.endOfLoopOffset, false));
            sub.keySet().forEach(code::remove);
            code.remove(iterator.endOfLoopOffset);
            code.put(i, new ForEach(iterator.expression, decompile(sub)));
        }
    };
}
