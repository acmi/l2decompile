package acmi.l2.clientmod.decompiler.bytecode;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import acmi.l2.clientmod.unreal.UnrealRuntimeContext;
import acmi.l2.clientmod.unreal.bytecode.token.Token;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import static acmi.l2.clientmod.decompiler.Util.newLine;

@RequiredArgsConstructor
@Getter
class For implements Loop {
    private final Token init;
    private final Token condition;
    private final Token post;
    private final NavigableMap<Integer, BytecodeEntry> body;

    @SneakyThrows(IOException.class)
    @Override
    public <T extends Appendable> T appendTo(T sb, UnrealRuntimeContext context, int indent) {
        sb.append("for ( ");
        if (init != null) {
            sb.append(init.toString(context)).append(" ");
        }
        sb.append("; ");
        if (condition != null) {
            sb.append(condition.toString(context)).append(" ");
        }
        sb.append("; ");
        if (post != null) {
            sb.append(post.toString(context)).append(" ");
        }
        sb.append(")");
        sb.append(newLine(indent)).append("{");
        for (BytecodeEntry e: body.values()) {
            sb.append(newLine(indent + 1));
            e.appendTo(sb, context, indent + 1);
        }
        sb.append(newLine(indent)).append("}");
        return sb;
    }

    static Decompiler decompiler = (i, t, code)->{
        if (t instanceof While) {
            Token condition = ((While) t).getCondition();
            Map.Entry<Integer, BytecodeEntry> preEntry = code.lowerEntry(i);
            if (preEntry == null){
                return;
            }
            BytecodeEntry pre = preEntry.getValue();
            BytecodeEntry last = ((While) t).getBody().lastEntry().getValue();

            if (pre instanceof BEToken && last instanceof BEToken) {
                List<Token> preList = extractTokens(((BEToken) pre).getToken());
                List<Token> lastList = extractTokens(((BEToken) last).getToken());

                preList.retainAll(lastList);

                if (preList.size() == 1) {
                    code.put(code.lowerKey(i), new For(((BEToken) pre).getToken(), condition, ((BEToken) last).getToken(), ((While) t).getBody()));
                    code.remove(i);
                    ((While) t).getBody().remove(((While) t).getBody().lastKey());
                }
            }
        }
    };

    private static List<Token> extractTokens(Token t) {
        return Stream.concat(
                Arrays.stream(t.getClass().getDeclaredFields())
                        .filter(it -> Token.class.isAssignableFrom(it.getType()))
                        .map(it -> {
                            try {
                                return (Token) it.get(t);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }),
                Arrays.stream(t.getClass().getDeclaredFields())
                        .filter(it -> it.getType().isArray() && Token.class.isAssignableFrom(it.getType().getComponentType()))
                        .flatMap(it -> {
                            try {
                                return Stream.of((Token[]) it.get(t));
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        })
        ).collect(Collectors.toList());
    }
}
