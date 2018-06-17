package acmi.l2.clientmod.decompiler.bytecode;

import java.io.IOException;

import acmi.l2.clientmod.unreal.UnrealRuntimeContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
@Getter
class Text implements BytecodeEntry {
    private final String text;

    @SneakyThrows(IOException.class)
    @Override
    public <T extends Appendable> T appendTo(T sb, UnrealRuntimeContext context, int indent) {
        sb.append(text);
        return sb;
    }

    @Override
    public String toString() {
        return text;
    }
}
