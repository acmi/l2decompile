/*
 * Copyright (c) 2016 acmi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package acmi.l2.clientmod.decompiler;

import acmi.l2.clientmod.unreal.core.Field;
import acmi.l2.clientmod.unreal.core.Object;
import acmi.l2.clientmod.unreal.core.Struct;

import java.text.DecimalFormat;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterator.*;

public class Util {
    private static DecimalFormat FORMAT_POLYGON_VECTOR = new DecimalFormat("00000.000000");
    static {
        Util.FORMAT_POLYGON_VECTOR.setPositivePrefix("+");
    }

    public static String formatVector(Object.Vector vector) {
        return Util.FORMAT_POLYGON_VECTOR.format(vector.x) + "," +
                Util.FORMAT_POLYGON_VECTOR.format(vector.y) + "," +
                Util.FORMAT_POLYGON_VECTOR.format(vector.z);
    }

    public static CharSequence tab(int indent) {
        StringBuilder sb = new StringBuilder(indent);
        for (int i = 0; i < indent; i++)
            sb.append('\t');
        return sb;
    }

    public static CharSequence newLine() {
        return newLine(0);
    }

    public static CharSequence newLine(int indent) {
        StringBuilder sb = new StringBuilder("\r\n");
        sb.append(tab(indent));
        return sb;
    }

    public static Stream<Field> children(Struct struct) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(struct.iterator(), DISTINCT | ORDERED | NONNULL), false);
    }
}
