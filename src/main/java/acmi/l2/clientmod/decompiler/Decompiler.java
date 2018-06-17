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

import acmi.l2.clientmod.decompiler.bytecode.ByteCodeDecompiler;
import acmi.l2.clientmod.io.UnrealPackage;
import acmi.l2.clientmod.unreal.UnrealRuntimeContext;
import acmi.l2.clientmod.unreal.UnrealSerializerFactory;
import acmi.l2.clientmod.unreal.bytecode.BytecodeContext;
import acmi.l2.clientmod.unreal.bytecode.token.Nothing;
import acmi.l2.clientmod.unreal.bytecode.token.Return;
import acmi.l2.clientmod.unreal.bytecode.token.Token;
import acmi.l2.clientmod.unreal.core.*;
import acmi.l2.clientmod.unreal.core.Class;
import acmi.l2.clientmod.unreal.core.Const;
import acmi.l2.clientmod.unreal.core.Enum;
import acmi.l2.clientmod.unreal.core.Object;
import acmi.l2.clientmod.unreal.engine.Polys;
import acmi.l2.clientmod.unreal.properties.L2Property;
import javafx.util.Pair;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.collections4.IterableUtils;

import static acmi.l2.clientmod.decompiler.Util.formatVector;
import static acmi.l2.clientmod.decompiler.Util.newLine;
import static acmi.l2.clientmod.decompiler.Util.children;
import static acmi.l2.clientmod.io.BufferUtil.getCompactInt;
import static acmi.l2.clientmod.unreal.core.Property.CPF.*;
import static acmi.l2.clientmod.unreal.core.Property.CPF.Deprecated;

@SuppressWarnings({"WeakerAccess", "unchecked", "UnusedParameters", "unused", "UnusedReturnValue"})
public class Decompiler {
    static Object instantiate(UnrealPackage.ExportEntry entry, UnrealSerializerFactory objectFactory) {
        return objectFactory.getOrCreateObject(entry);
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T decompile(T sb, Class clazz, UnrealSerializerFactory objectFactory, int indent) {
        String name = clazz.entry.getObjectName().getName();
        String superName = clazz.entry.getObjectSuperClass() != null ?
                clazz.entry.getObjectSuperClass().getObjectName().getName() : null;

        sb.append("class ").append(name);
        if (superName != null)
            sb.append(" extends ").append(superName);
        //TODO flags
        sb.append(";");

        if (clazz.child != null) {
            sb.append(newLine());
            decompileFields(sb, clazz, objectFactory, indent);
        }

        if (!clazz.properties.isEmpty()) {
            sb.append(newLine());
            sb.append(newLine(indent)).append("defaultproperties{");
            sb.append(newLine(indent + 1));
            decompileProperties(sb, clazz, objectFactory, indent + 1, false);
            sb.append(newLine(indent)).append("}");
        }

        return sb;
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T decompileFields(T sb, Struct struct, UnrealSerializerFactory objectFactory, int indent) {
        List<Field> children = IterableUtils.toList(struct);

        if (struct instanceof State) {
            Collections.reverse(children);
        }
        children.sort(Comparator.comparingInt(Decompiler::getOrder));

        for (Field field: children) {
            sb.append(newLine(indent));
            if (field instanceof Const) {
                decompileConst(sb, (Const) field, objectFactory, indent);
                sb.append(";");
            } else if (field instanceof Enum) {
                if (!struct.getClass().equals(Struct.class)) {
                    decompileEnum(sb, (Enum) field, objectFactory, indent);
                    sb.append(";");
                }
            } else if (field instanceof Property) {
                if (field instanceof DelegateProperty)
                    continue;

                decompileProperty(sb, (Property) field, struct, objectFactory, indent);
                sb.append(";");
            } else if (field instanceof State) {
                decompileState(sb, (State) field, objectFactory, indent);
            } else if (field instanceof Function) {
                decompileFunction(sb, (Function) field, objectFactory, indent);
            } else if (field instanceof Struct) {
                decompileStruct(sb, (Struct) field, objectFactory, indent);
                sb.append(";");
            } else {
                sb.append(field.toString());
            }
        }

        return sb;
    }

    private static int getOrder(Field field) {
        if (field instanceof Const){
            return 0;
        } else if (field instanceof Enum){
            return 1;
        } else if (field instanceof Struct && !(field instanceof Function) && !(field instanceof State)) {
            return 2;
        } else if (field instanceof Property) {
            return 3;
        } else if (field instanceof Function) {
            return 4;
        } else if (field instanceof State) {
            return 5;
        } else {
            return 6;
        }
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T decompileConst(T sb, Const c, UnrealSerializerFactory objectFactory, int indent) {
        sb.append("const ")
                .append(c.entry.getObjectName().getName())
                .append(" = ")
                .append(c.value);
        return sb;
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T decompileEnum(T sb, Enum e, UnrealSerializerFactory objectFactory, int indent) {
        sb.append("enum ").append(e.entry.getObjectName().getName())
                .append(newLine(indent)).append("{")
                .append(newLine(indent + 1)).append(Arrays.stream(e.values).collect(Collectors.joining("," + newLine(indent + 1))))
                .append(newLine(indent)).append("}");
        return sb;
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T decompileProperty(T sb, Property property, Struct parent, UnrealSerializerFactory objectFactory, int indent) {
        sb.append("var");
        CharSequence type = getType(new StringBuilder(), property, objectFactory, true);
        sb.append(" ").append(type).append(" ");
        sb.append(property.entry.getObjectName().getName());
        if (property.arrayDimension > 1)
            sb.append("[").append(String.valueOf(property.arrayDimension)).append("]");

        return sb;
    }

    private static final List<Pair<Predicate<Property>, java.util.function.Function<Property, String>>> MODIFIERS = Arrays.asList(
            new Pair<>(p -> getFlags(p.propertyFlags).contains(Edit), p -> "(" + (p.entry.getObjectPackage().getObjectName().getName().equalsIgnoreCase(p.category) ? "" : p.category) + ")"),
            new Pair<>(p -> UnrealPackage.ObjectFlag.getFlags(p.entry.getObjectFlags()).contains(UnrealPackage.ObjectFlag.Private), p -> "private"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(Const), p -> "const"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(Input), p -> "input"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(ExportObject), p -> "export"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(OptionalParm), p -> "optional"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(OutParm), p -> "out"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(SkipParm), p -> "skip"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(CoerceParm), p -> "coerce"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(Native), p -> "native"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(Transient), p -> "transient"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(Config), p -> getFlags(p.propertyFlags).contains(GlobalConfig) ? null : "config"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(Localized), p -> "localized"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(Travel), p -> "travel"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(EditConst), p -> "editconst"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(GlobalConfig), p -> "globalconfig"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(EditInline), p -> getFlags(p.propertyFlags).contains(EditInlineUse) ? null : "editinline"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(EdFindable), p -> "edfindable"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(EditInlineUse), p -> "editinlineuse"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(Deprecated), p -> "deprecated"),
            new Pair<>(p -> getFlags(p.propertyFlags).contains(EditInlineNotify), p -> "editinlinenotify")
    );

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T getType(T sb, Property property, UnrealSerializerFactory objectFactory, boolean includeModifiers) {
        if (includeModifiers) {
            MODIFIERS.stream()
                    .filter(p -> p.getKey().test(property))
                    .map(p -> p.getValue().apply(property))
                    .filter(Objects::nonNull)
                    .forEach(m -> {
                        try {
                            sb.append(m);
                            sb.append(" ");
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
        if (property instanceof ByteProperty) {
            if (((ByteProperty) property).enumType != null) {
                UnrealPackage.Entry enumLocalEntry = ((ByteProperty) property).enumType.entry;
                sb.append(enumLocalEntry.getObjectName().getName());
            } else {
                sb.append("byte");
            }
        } else if (property instanceof IntProperty) {
            sb.append("int");
        } else if (property instanceof BoolProperty) {
            sb.append("bool");
        } else if (property instanceof FloatProperty) {
            sb.append("float");
        } else if (property instanceof ClassProperty) {
            sb.append("class<");
            sb.append(getName(((ClassProperty) property).clazz));
            sb.append(">");
        } else if (property instanceof ObjectProperty) {
            sb.append(getName(((ObjectProperty) property).type));
        } else if (property instanceof NameProperty) {
            sb.append("name");
        } else if (property instanceof ArrayProperty) {
            ArrayProperty arrayProperty = (ArrayProperty) property;
            Property innerProperty = arrayProperty.inner;
            sb.append("array<");
            getType(sb, innerProperty, objectFactory, false);
            sb.append(">");
        } else if (property instanceof StructProperty) {
            sb.append(getName(((StructProperty) property).struct));
        } else if (property instanceof StrProperty) {
            sb.append("string");
        }

        return sb;
    }

    private static String getName(Struct struct) {
        if (struct.friendlyName != null) {
            return struct.friendlyName;
        } else {
            // native struct not presented in .u
            String[] name = struct.getFullName().split("\\.");
            return name[name.length - 1];
        }
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T decompileStruct(T sb, Struct struct, UnrealSerializerFactory objectFactory, int indent) {
        sb.append("struct ").append(struct.entry.getObjectName().getName());
        sb.append(newLine(indent)).append("{");
        decompileFields(sb, struct, objectFactory, indent + 1);
        sb.append(newLine(indent)).append("}");
        return sb;
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T decompileFunction(T sb, Function function, UnrealSerializerFactory objectFactory, int indent) {
        UnrealRuntimeContext context = new UnrealRuntimeContext(function.entry, objectFactory);

        Collection<Function.Flag> functionFlags = Function.Flag.getFlags(function.functionFlags);
        if (functionFlags.contains(Function.Flag.NATIVE)) {
            sb.append("native");
            if (function.nativeIndex != 0) {
                sb.append("(").append(String.valueOf(function.nativeIndex)).append(")");
            }
            sb.append(" ");
        }
        if (functionFlags.contains(Function.Flag.SIMULATED)) {
            sb.append("simulated ");
        }
        if (functionFlags.contains(Function.Flag.STATIC)) {
            sb.append("static ");
        }
        if (functionFlags.contains(Function.Flag.FINAL)) {
            sb.append("final ");
        }
        if (functionFlags.contains(Function.Flag.ITERATOR)) {
            sb.append("iterator ");
        }
        if (functionFlags.contains(Function.Flag.LATENT)) {
            sb.append("latent ");
        }
        if (functionFlags.contains(Function.Flag.EXEC)) {
            sb.append("exec ");
        }
        if (functionFlags.contains(Function.Flag.EVENT)) {
            sb.append("event ");
        } else if (functionFlags.contains(Function.Flag.OPERATOR)) {
            sb.append("operator(").append(String.valueOf(function.operatorPrecedence)).append(") ");
        } else {
            sb.append("function ");
        }
        children(function)
                .filter(it -> it instanceof Property)
                .map(it -> (Property) it)
                .filter(it -> Property.CPF.getFlags(it.propertyFlags).contains(Property.CPF.ReturnParm))
                .findAny()
                .ifPresent(it -> {
                    try {
                        getType(sb, it, objectFactory, false);
                        sb.append(" ");
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        sb.append(function.friendlyName).append("(");
        sb.append(children(function)
                .filter(it -> it instanceof Property)
                .map(it -> (Property) it)
                .filter(it -> {
                    Collection<Property.CPF> flags = Property.CPF.getFlags(it.propertyFlags);
                    return flags.contains(Property.CPF.Parm) && !flags.contains(ReturnParm);
                })
                .map(it -> {
                    StringBuilder sb1 = new StringBuilder();
                    Collection<Property.CPF> propertyFlags = Property.CPF.getFlags(it.propertyFlags);
                    if (propertyFlags.contains(OptionalParm)) {
                        sb1.append("optional ");
                    }
                    if (propertyFlags.contains(OutParm)) {
                        sb1.append("out ");
                    }
                    getType(sb1, it, objectFactory, false);
                    sb1.append(" ").append(it.entry.getObjectName().getName());
                    return sb1;
                })
                .collect(Collectors.joining(", ")));
        sb.append(")");
        if (functionFlags.contains(Function.Flag.NATIVE)) {
            sb.append(";");
            return sb;
        }
        if (function.bytecode[0] instanceof Return){
            Return r = (Return)function.bytecode[0];
            if (r.value instanceof Nothing){
                sb.append(";");
                return sb;
            }
        }
        sb.append(newLine(indent)).append("{");
        LinkedHashMap<String, List<Property>> localVars = children(function)
                .filter(it -> it instanceof Property)
                .map(it -> (Property) it)
                .filter(it -> !Property.CPF.getFlags(it.propertyFlags).contains(Parm))
                .collect(Collectors.groupingBy(it -> getType(new StringBuilder(), it, objectFactory, false).toString(), LinkedHashMap::new, Collectors.toList()));
        if (!localVars.isEmpty()) {
            for (Map.Entry<String, List<Property>> e: localVars.entrySet()) {
                sb.append(newLine(indent + 1))
                        .append("local ").append(e.getKey()).append(" ")
                        .append(e.getValue().stream()
                                .map(it -> it.entry.getObjectName().getName())
                                .collect(Collectors.joining(", ")))
                        .append(";");
            }
            sb.append(newLine());
        }

        NavigableMap<Integer, Token> code = new TreeMap<>();
        int s = 0;
        for (Token t: function.bytecode) {
            code.put(s, t);
            s += t.getSize(new BytecodeContext(function.entry.getUnrealPackage()));
        }

        ByteCodeDecompiler.decompileBytecode(sb, code, context, indent+1);

        sb.append(newLine(indent)).append("}");

        return sb;
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T decompileState(T sb, State state, UnrealSerializerFactory objectFactory, int indent) {
        sb.append("state ");
        sb.append(state.entry.getObjectName().getName());
        if (state.entry.getObjectSuperClass() != null) {
            sb.append(" extends ").append(state.entry.getObjectSuperClass().getObjectName().getName());
        }
        sb.append(newLine(indent)).append("{");
        decompileFields(sb, state, objectFactory, indent + 1);
//        ByteCodeDecompiler.decompileBytecode() TODO
        sb.append(newLine(indent)).append("}");

        return sb;
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T decompileProperties(T sb1, Object object, UnrealSerializerFactory objectFactory, int indent, boolean map) {
        Stream.Builder<CharSequence> properties = Stream.builder();

        UnrealPackage up = object.entry.getUnrealPackage();

        object.properties.forEach(property -> {
            StringBuilder sb = new StringBuilder();

            Property template = property.getTemplate();

            for (int i = 0; i < template.arrayDimension; i++) {
                java.lang.Object obj = property.getAt(i);

                if (obj == null) {
                    if (template instanceof StructProperty)
                        continue;
                }

                if (i > 0)
                    sb.append(newLine(indent));

                if (template instanceof ByteProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("(").append(i).append(")");
                    }
                    sb.append("=");
                    if (((ByteProperty) template).enumType != null) {
                        Enum en = ((ByteProperty) template).enumType;
                        sb.append(en.values[(Integer) obj]);
                    } else {
                        sb.append(Optional.ofNullable(obj).orElse(0));
                    }
                } else if (template instanceof IntProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("(").append(i).append(")");
                    }
                    sb.append("=");
                    sb.append(Optional.ofNullable(obj).orElse(0));
                } else if (template instanceof BoolProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("(").append(i).append(")");
                    }
                    sb.append("=");
                    sb.append(Optional.ofNullable(obj).orElse(false));
                } else if (template instanceof FloatProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("(").append(i).append(")");
                    }
                    sb.append("=");
                    sb.append(String.format(Locale.US, "%f", (Float) Optional.ofNullable(obj).orElse(0f)));
                } else if (template instanceof ObjectProperty) {
                    UnrealPackage.Entry entry = up.objectReference((Integer) Optional.ofNullable(obj).orElse(0));
                    if (needExport(entry, template)) {
                        properties.add(toT3d(new StringBuilder(), instantiate((UnrealPackage.ExportEntry) entry, objectFactory), objectFactory, indent, map));
                    }
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("(").append(i).append(")");
                    }
                    sb.append("=");
                    linkTo(sb, entry, map);
                } else if (template instanceof NameProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("(").append(i).append(")");
                    }
                    sb.append("=");
                    sb.append("\"").append(up.nameReference((Integer) obj)).append("\"");
                } else if (template instanceof ArrayProperty) {
                    ArrayProperty arrayProperty = (ArrayProperty) property.getTemplate();
                    Property innerProperty = arrayProperty.inner;
                    L2Property fakeProperty = new L2Property(innerProperty);
                    List<java.lang.Object> list = (List<java.lang.Object>) obj;
                    if (list != null) {
                        for (int j = 0; j < list.size(); j++) {
                            java.lang.Object innerObj = list.get(j);

                            if (innerProperty instanceof ObjectProperty) {
                                UnrealPackage.Entry entry = up.objectReference((Integer) innerObj);
                                if (needExport(entry, innerProperty)) {
                                    properties.add(toT3d(new StringBuilder(), instantiate((UnrealPackage.ExportEntry) entry, objectFactory), objectFactory, indent, map));
                                }
                            }

                            fakeProperty.putAt(0, innerObj);
                            if (j > 0)
                                sb.append(newLine(indent));
                            sb.append(property.getName()).append("(").append(j).append(")").append("=");
                            inlineProperty(sb, fakeProperty, up, objectFactory, true, map);
                        }
                    }
                } else if (template instanceof StructProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("(").append(i).append(")");
                    }
                    sb.append("=");
                    inlineStruct(sb, (List<L2Property>) obj, up, objectFactory, map);
                } else if (template instanceof StrProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("(").append(i).append(")");
                    }
                    sb.append("=");
                    sb.append("\"").append(Objects.toString(obj)).append("\"");
                }
            }

            properties.add(sb);
        });

        sb1.append(properties.build().collect(Collectors.joining(newLine(indent))));

        return sb1;
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T inlineProperty(T sb1, L2Property property, UnrealPackage up, UnrealSerializerFactory objectFactory, boolean valueOnly, boolean map) {
        Property template = property.getTemplate();
        sb1.append(IntStream.range(0, template.arrayDimension)
                .mapToObj(i -> new IndexObject(i, property.getAt(i)))
                .filter(o -> o.getObject() != null)
                .map(e -> {
                    int i = e.getIndex();
                    java.lang.Object object = e.getObject();
                    StringBuilder sb = new StringBuilder();
                    if (!valueOnly) {
                        sb.append(property.getName());

                        if (template.arrayDimension > 1) {
                            sb.append("(").append(i).append(")");
                        }

                        sb.append("=");
                    }

                    if (template instanceof ByteProperty) {
                        if (((ByteProperty) template).enumType != null) {
                            Enum en = ((ByteProperty) template).enumType;
                            sb.append(en.values[(Integer) object]);
                        } else {
                            sb.append(object);
                        }
                    } else if (template instanceof IntProperty ||
                            template instanceof BoolProperty) {
                        sb.append(object);
                    } else if (template instanceof FloatProperty) {
                        sb.append(String.format(Locale.US, "%f", (Float) object));
                    } else if (template instanceof ObjectProperty) {
                        UnrealPackage.Entry entry = up.objectReference((Integer) object);
                        linkTo(sb, entry, map);
                    } else if (template instanceof NameProperty) {
                        sb.append("\"").append(up.nameReference((Integer) object)).append("\"");
                    } else if (template instanceof ArrayProperty) {
                        ArrayProperty arrayProperty = (ArrayProperty) property.getTemplate();
                        Property innerProperty = arrayProperty.inner;
                        L2Property fakeProperty = new L2Property(innerProperty);
                        List<java.lang.Object> list = (List<java.lang.Object>) object;

                        sb.append(list.stream()
                                .map(o -> {
                                    fakeProperty.putAt(0, o);
                                    return inlineProperty(new StringBuilder(), fakeProperty, up, objectFactory, true, map);
                                }).collect(Collectors.joining(",", "(", ")")));
                    } else if (template instanceof StructProperty) {
                        if (object == null) {
                            sb.append("None");
                        } else {
                            inlineStruct(sb, (List<L2Property>) object, up, objectFactory, map);
                        }
                    } else if (template instanceof StrProperty) {
                        sb.append("\"").append(Objects.toString(object)).append("\"");
                    }
                    return sb;
                })
                .collect(Collectors.joining(",")));
        return sb1;
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T linkTo(T sb, UnrealPackage.Entry entry, boolean map) {
        if (entry == null) {
            sb.append("None");
        } else if (entry instanceof UnrealPackage.ImportEntry) {
            sb.append(((UnrealPackage.ImportEntry) entry).getClassName().getName()).append("'").append(entry.getObjectFullName()).append("'");
        } else if (entry instanceof UnrealPackage.ExportEntry) {
            String clazz = "Class";
            if (((UnrealPackage.ExportEntry) entry).getObjectClass() != null)
                clazz = ((UnrealPackage.ExportEntry) entry).getObjectClass().getObjectName().getName();
            sb.append(clazz).append("'").append(map ? "myLevel." + entry.getObjectInnerFullName() : entry.getObjectFullName()).append("'");
        } else {
            throw new IllegalStateException("Unknown entry class: " + entry.getClass());
        }
        return sb;
    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T inlineStruct(T sb, List<L2Property> struct, UnrealPackage up, UnrealSerializerFactory objectFactory, boolean map) {
        sb.append(struct.stream().map(p -> inlineProperty(new StringBuilder(), p, up, objectFactory, false, map)).collect(Collectors.joining(",", "(", ")")));
        return sb;
    }

    public static boolean needExport(UnrealPackage.Entry entry, Property template) {
        return entry instanceof UnrealPackage.ExportEntry &&
                (getFlags(template.propertyFlags).contains(ExportObject) ||
                        getFlags(template.propertyFlags).contains(EditInlineNotify)); //FIXME

    }

    @SneakyThrows(IOException.class)
    public static <T extends Appendable> T toT3d(T sb, Object object, UnrealSerializerFactory objectFactory, int indent, boolean map) {
        String clazz = object.entry.getObjectClass().getObjectName().getName();
        String name = object.entry.getObjectName().getName();
        if ("Model".equalsIgnoreCase(clazz)) {
            sb.append("Begin Brush Name=").append(name);
            sb.append(newLine(indent + 1)).append("Begin PolyList");

            //TODO Model support
            ByteBuffer buffer = ByteBuffer.wrap(object.unreadBytes);
            Polys polys = null;
            for (int i = buffer.limit() - 1; i > 0; i--) {
                try {
                    buffer.position(i);

                    int ref = getCompactInt(buffer);
                    polys = (Polys) Optional.of(ref)
                            .map(object.entry.getUnrealPackage()::objectReference)
                            .filter(it -> "Engine.Polys".equalsIgnoreCase(it.getFullClassName()))
                            .map(objectFactory::getOrCreateObject)
                            .orElse(null);
                    if (polys != null) {
                        break;
                    }
                } catch (Throwable ignore) {
                }
            }
            if (polys == null)
                throw new IllegalStateException("Polys not found in " + object.entry.getObjectFullName());

            for (Polys.Polygon polygon: polys.polygons) {
                sb.append(newLine(indent + 2)).append("Begin Polygon");
                if (!"None".equalsIgnoreCase(polygon.itemName)) {
                    sb.append(" Item=").append(polygon.itemName);
                }
                if (polygon.texture != null) {
                    sb.append(" Texture=").append(polygon.texture.entry.getObjectFullName());
                }
                if (polygon.flags != 0) {
                    sb.append(" Flags=").append(String.valueOf(polygon.flags));
                }
                sb.append(" Link=").append(String.valueOf(polygon.iLink));
                sb.append(newLine(indent + 3)).append("Origin   ").append(formatVector(polygon.origin));
                sb.append(newLine(indent + 3)).append("Normal   ").append(formatVector(polygon.normal));
                sb.append(newLine(indent + 3)).append("TextureU ").append(formatVector(polygon.textureU));
                sb.append(newLine(indent + 3)).append("TextureV ").append(formatVector(polygon.textureV));
                for (Object.Vector vertex: polygon.vertexes)
                    sb.append(newLine(indent + 3)).append("Vertex   ").append(formatVector(vertex));
                sb.append(newLine(indent + 2)).append("End Polygon");
            }

            sb.append(newLine(indent + 1)).append("End PolyList");
            sb.append(newLine(indent)).append("End Brush");
        } else {
            String type = objectFactory.isSubclass("Engine.Actor", object.entry.getFullClassName()) ? "Actor" : "Object";
            sb.append("Begin ").append(type).append(" Class=").append(clazz).append(" Name=").append(name);
            sb.append(newLine(indent + 1));
            decompileProperties(sb, object, objectFactory, indent + 1, map);
            sb.append(newLine(indent)).append("End ").append(type);
        }

        return sb;
    }
}