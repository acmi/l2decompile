package acmi.l2.clientmod.decompiler.bytecode;

import java.util.NavigableMap;

public interface Decompiler {
    void accept(Integer offset, BytecodeEntry entry, NavigableMap<Integer, BytecodeEntry> code);
}
