package acmi.l2.clientmod.decompiler.bytecode;

import java.util.NavigableMap;

interface Loop extends BytecodeEntry {
    NavigableMap<Integer, BytecodeEntry> getBody();
}
