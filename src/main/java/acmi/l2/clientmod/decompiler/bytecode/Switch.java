package acmi.l2.clientmod.decompiler.bytecode;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import acmi.l2.clientmod.unreal.UnrealRuntimeContext;
import acmi.l2.clientmod.unreal.bytecode.token.Case;
import acmi.l2.clientmod.unreal.bytecode.token.Jump;
import acmi.l2.clientmod.unreal.bytecode.token.Token;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import static acmi.l2.clientmod.decompiler.Util.newLine;

@RequiredArgsConstructor
class Switch implements BytecodeEntry {
    private final Token expression;
    private final Map<Case, NavigableMap<Integer, BytecodeEntry>> caseMap;

    @SneakyThrows(IOException.class)
    @Override
    public <T extends Appendable> T appendTo(T sb, UnrealRuntimeContext context, int indent) {
        sb.append("switch ( ").append(expression.toString(context)).append(" )");
        sb.append(newLine(indent)).append("{");
        for (Map.Entry<Case, NavigableMap<Integer, BytecodeEntry>> caseEntry: caseMap.entrySet()) {
            sb.append(newLine(indent + 1)).append(caseEntry.getKey().toString(context));
            for (BytecodeEntry e: caseEntry.getValue().values()) {
                sb.append(newLine(indent + 2));
                e.appendTo(sb, context, indent + 2);
            }
        }
        sb.append(newLine(indent)).append("}");
        return sb;
    }

    static Decompiler decompiler = (i, t, code) -> {
        if (t instanceof BEToken && ((BEToken) t).getToken() instanceof acmi.l2.clientmod.unreal.bytecode.token.Switch) {
            Token switchExpr = ((acmi.l2.clientmod.unreal.bytecode.token.Switch) ((BEToken) t).getToken()).expression;
            Map<Case, NavigableMap<Integer, BytecodeEntry>> caseMap = new LinkedHashMap<>();
            NavigableMap<Integer, BytecodeEntry> currentCaseList = new TreeMap<>();
            boolean lastCase = false;
            boolean end = false;

            for (Integer j = code.higherKey(i); j != null && j <= code.lastKey() && !end; j = code.higherKey(j)) {
                t = code.remove(j);
                if (t instanceof BEToken) {
                    if (((BEToken) t).getToken() instanceof Case) {
                        currentCaseList = new TreeMap<>();
                        caseMap.put((Case) ((BEToken) t).getToken(), currentCaseList);
                        if (((Case) ((BEToken) t).getToken()).nextOffset == Case.DEFAULT) {
                            lastCase = true;
                        }
                        continue;
                    } else if (((BEToken) t).getToken() instanceof Jump) {
                        currentCaseList.put(j, new Text("break;"));
                        if (lastCase) {
                            end = true;
                        }
                        continue;
                    }
                }
                currentCaseList.put(j, t);
            }

            code.put(i, new Switch(switchExpr, caseMap));
        }
    };
}
