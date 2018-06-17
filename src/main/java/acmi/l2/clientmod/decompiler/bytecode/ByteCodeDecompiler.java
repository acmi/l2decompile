package acmi.l2.clientmod.decompiler.bytecode;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Consumer;

import acmi.l2.clientmod.unreal.UnrealRuntimeContext;
import acmi.l2.clientmod.unreal.bytecode.token.Nothing;
import acmi.l2.clientmod.unreal.bytecode.token.Return;
import acmi.l2.clientmod.unreal.bytecode.token.Token;
import lombok.SneakyThrows;

import static acmi.l2.clientmod.decompiler.Util.newLine;

@SuppressWarnings("UnusedReturnValue")
public class ByteCodeDecompiler {

    private static final List<Decompiler> decompilers = Arrays.asList(
            ForEach.decompiler,
            DoUntil.decompiler,
            If.decompilerIfElse,
            While.decompiler,
            For.decompiler,
            If.decompilerIf,
            Switch.decompiler
    );

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T decompileBytecode(T sb, NavigableMap<Integer, Token> code, UnrealRuntimeContext context, int indent) {
        // remove last Return(Nothing())
        code = new TreeMap<>(code);
        Token last = code.lastEntry().getValue();
        if (last instanceof Return && ((Return) last).value instanceof Nothing) {
            code.remove(code.lastKey());
        }

        // wrap tokens
        NavigableMap<Integer, BytecodeEntry> beMap = new TreeMap<>();
        code.forEach((k, v) -> beMap.put(k, new BEToken(v)));

        // decompile & optimize
        NavigableMap<Integer, BytecodeEntry> result = decompile(beMap);

        // print
        for (BytecodeEntry e: result.values()) {
            sb.append(newLine(indent));
            e.appendTo(sb, context, indent);
        }
        return sb;
    }

    static NavigableMap<Integer, BytecodeEntry> decompile(NavigableMap<Integer, BytecodeEntry> code) {
        if (!code.isEmpty()) {
            Consumer<Decompiler> entryLoop = (p) -> {
                for (Integer i = code.firstKey(); i != null && i <= code.lastKey(); i = code.higherKey(i)) {
                    BytecodeEntry t = code.get(i);
                    p.accept(i, t, code);
                }
            };

            decompilers.forEach(entryLoop);
        }
        return code;
    }
}
