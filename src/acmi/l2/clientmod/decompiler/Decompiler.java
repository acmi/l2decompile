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

import acmi.l2.clientmod.io.UnrealPackage;
import acmi.l2.clientmod.unreal.UnrealSerializerFactory;
import acmi.l2.clientmod.unreal.core.*;
import acmi.l2.clientmod.unreal.core.Class;
import acmi.l2.clientmod.unreal.core.Enum;
import acmi.l2.clientmod.unreal.core.Object;
import acmi.l2.clientmod.unreal.properties.L2Property;
import javafx.util.Pair;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static acmi.l2.clientmod.decompiler.Util.newLine;

public class Decompiler {
    static Object instantiate(UnrealPackage.ExportEntry entry, UnrealSerializerFactory objectFactory) {
        return objectFactory.getOrCreateObject(entry);
    }

    public static CharSequence decompile(Class clazz, UnrealSerializerFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

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
            sb.append(newLine(indent)).append(decompileFields(clazz, objectFactory, indent));
        }

        if (!clazz.properties.isEmpty()) {
            sb.append(newLine());
            sb.append(newLine(indent)).append("defaultproperties{");
            sb.append(newLine(indent + 1)).append(decompileProperties(clazz, objectFactory, indent + 1));
            sb.append(newLine(indent)).append("}");
        }

        return sb;
    }

    public static CharSequence decompileFields(Struct struct, UnrealSerializerFactory objectFactory, int indent) {
        Stream.Builder<CharSequence> fields = Stream.builder();

        struct.forEach(field -> {
            if (field instanceof Const) {
                fields.add(decompileConst((Const) field, objectFactory, indent) + ";");
            } else if (field instanceof Enum) {
                if (!struct.getClass().equals(Struct.class))
                    fields.add(decompileEnum((Enum) field, objectFactory, indent) + ";");
            } else if (field instanceof Property) {
                if (field instanceof DelegateProperty)
                    return;

                fields.add(decompileProperty((Property) field, struct, objectFactory, indent) + ";");
            } else if (field instanceof State) {
                fields.add(decompileState((State) field, objectFactory, indent));
            } else if (field instanceof Function) {
                fields.add(decompileFunction((Function) field, objectFactory, indent));
            } else if (field instanceof Struct) {
                fields.add(decompileStruct((Struct) field, objectFactory, indent) + ";");
            } else {
                fields.add(field.toString());
            }
        });

        return fields.build().collect(Collectors.joining(newLine(indent)));
    }

    public static CharSequence decompileConst(Const c, UnrealSerializerFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append("const ")
                .append(c.entry.getObjectName().getName())
                .append(" =")
                .append(c.value);

        return sb;
    }

    public static CharSequence decompileEnum(Enum e, UnrealSerializerFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append("enum ").append(e.entry.getObjectName().getName())
                .append(newLine(indent)).append("{")
                .append(newLine(indent + 1)).append(Arrays.stream(e.values).collect(Collectors.joining("," + newLine(indent + 1))))
                .append(newLine(indent)).append("}");

        return sb;
    }

    public static CharSequence decompileProperty(Property property, Struct parent, UnrealSerializerFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append("var");
        CharSequence type = getType(property, objectFactory, true);
        if (parent.getClass().equals(Struct.class)) {
            if (property instanceof ByteProperty &&
                    ((ByteProperty) property).enumType != null) {
                Enum en = ((ByteProperty) property).enumType;
                type = decompileEnum(en, objectFactory, indent);
            }
            //FIXME array<enum>
        }
        sb.append(" ").append(type).append(" ");
        sb.append(property.entry.getObjectName().getName());
        if (property.arrayDimension > 1)
            sb.append("[").append(property.arrayDimension).append("]");

        return sb;
    }

    private static final List<Pair<Predicate<Property>, java.util.function.Function<Property, String>>> MODIFIERS = Arrays.asList(
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.Edit), p -> "(" + (p.entry.getObjectPackage().getObjectName().getName().equalsIgnoreCase(p.category) ? "" : p.category) + ")"),
            new Pair<>(p -> UnrealPackage.ObjectFlag.getFlags(p.entry.getObjectFlags()).contains(UnrealPackage.ObjectFlag.Private), p -> "private"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.Const), p -> "const"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.Input), p -> "input"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.ExportObject), p -> "export"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.OptionalParm), p -> "optional"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.OutParm), p -> "out"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.SkipParm), p -> "skip"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.CoerceParm), p -> "coerce"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.Native), p -> "native"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.Transient), p -> "transient"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.Config), p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.GlobalConfig) ? null : "config"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.Localized), p -> "localized"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.Travel), p -> "travel"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.EditConst), p -> "editconst"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.GlobalConfig), p -> "globalconfig"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.EditInline), p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.EditInlineUse) ? null : "editinline"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.EdFindable), p -> "edfindable"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.EditInlineUse), p -> "editinlineuse"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.Deprecated), p -> "deprecated"),
            new Pair<>(p -> Property.CPF.getFlags(p.propertyFlags).contains(Property.CPF.EditInlineNotify), p -> "editinlinenotify")
    );

    public static CharSequence getType(Property property, UnrealSerializerFactory objectFactory, boolean includeModifiers) {
        StringBuilder sb = new StringBuilder();

        if (includeModifiers) {
            MODIFIERS.stream()
                    .filter(p -> p.getKey().test(property))
                    .map(p -> p.getValue().apply(property))
                    .forEach(m -> sb.append(m).append(" "));
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
        } else if (property instanceof ObjectProperty) {
            sb.append(((ObjectProperty) property).type.entry.getObjectName().getName());
        } else if (property instanceof NameProperty) {
            sb.append("name");
        } else if (property instanceof ArrayProperty) {
            ArrayProperty arrayProperty = (ArrayProperty) property;
            Property innerProperty = arrayProperty.inner;
            sb.append("array<").append(getType(innerProperty, objectFactory, false)).append(">");
        } else if (property instanceof StructProperty) {
            sb.append(((StructProperty) property).struct.entry.getObjectName().getName());
        } else if (property instanceof StrProperty) {
            sb.append("string");
        }

        return sb;
    }

    public static CharSequence decompileStruct(Struct struct, UnrealSerializerFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("struct ").append(struct.entry.getObjectName().getName());
        sb.append(newLine(indent)).append("{");
        sb.append(newLine(indent + 1)).append(decompileFields(struct, objectFactory, indent + 1));
        sb.append(newLine(indent)).append("}");
        return sb;
    }

    public static CharSequence decompileFunction(Function function, UnrealSerializerFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append("//function_").append(function.friendlyName); //TODO

        return sb;
    }

    public static CharSequence decompileState(State state, UnrealSerializerFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append("state ");
        sb.append(state.entry.getObjectName().getName());
        if (state.entry.getObjectSuperClass() != null) {
            sb.append(" extends ").append(state.entry.getObjectSuperClass().getObjectName().getName());
        }
        sb.append(newLine(indent)).append("{");
        sb.append(newLine(indent + 1)).append(decompileFields(state, objectFactory, indent + 1));
        sb.append(newLine(indent)).append("}");

        return sb;
    }

    public static CharSequence decompileProperties(Object object, UnrealSerializerFactory objectFactory, int indent) {
        Stream.Builder<CharSequence> properties = Stream.builder();

        UnrealPackage up = object.entry.getUnrealPackage();

        object.properties.forEach(property -> {
            StringBuilder sb = new StringBuilder();

            Property template = property.getTemplate();

            for (int i = 0; i < template.arrayDimension; i++) {
                java.lang.Object obj = property.getAt(i);

                if (template instanceof ByteProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("[").append(i).append("]");
                    }
                    sb.append("=");
                    if (((ByteProperty) template).enumType != null) {
                        Enum en = ((ByteProperty) template).enumType;
                        sb.append(en.values[(Integer) obj]);
                    } else {
                        sb.append(obj);
                    }
                } else if (template instanceof IntProperty ||
                        template instanceof BoolProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("[").append(i).append("]");
                    }
                    sb.append("=");
                    sb.append(obj);
                } else if (template instanceof FloatProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("[").append(i).append("]");
                    }
                    sb.append("=");
                    sb.append(String.format(Locale.US, "%f", (Float) obj));
                } else if (template instanceof ObjectProperty) {
                    UnrealPackage.Entry entry = up.objectReference((Integer) obj);
                    if (needExport(entry, template)) {
                        properties.add(toT3d(instantiate((UnrealPackage.ExportEntry) entry, objectFactory), objectFactory, indent));
                    }
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("[").append(i).append("]");
                    }
                    sb.append("=");
                    if (entry == null) {
                        sb.append("None");
                    } else if (entry instanceof UnrealPackage.ImportEntry) {
                        sb.append(((UnrealPackage.ImportEntry) entry).getClassName().getName())
                                .append("'")
                                .append(entry.getObjectFullName())
                                .append("'");
                    } else if (entry instanceof UnrealPackage.ExportEntry) {
                        String clazz = "Class";
                        if (((UnrealPackage.ExportEntry) entry).getObjectClass() != null)
                            clazz = ((UnrealPackage.ExportEntry) entry).getObjectClass().getObjectName().getName();
                        sb.append(clazz)
                                .append("'")
                                .append(entry.getObjectInnerFullName())
                                .append("'");
                    } else {
                        throw new IllegalStateException("wtf");
                    }
                } else if (template instanceof NameProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("[").append(i).append("]");
                    }
                    sb.append("=");
                    sb.append("'").append(Objects.toString(obj)).append("'");
                } else if (template instanceof ArrayProperty) {
                    ArrayProperty arrayProperty = (ArrayProperty) property.getTemplate();
                    Property innerProperty = arrayProperty.inner;
                    L2Property fakeProperty = new L2Property(innerProperty);
                    List<java.lang.Object> list = (List<java.lang.Object>) obj;

                    for (int j = 0; j < list.size(); j++) {
                        java.lang.Object innerObj = list.get(j);

                        if (innerProperty instanceof ObjectProperty) {
                            UnrealPackage.Entry entry = up.objectReference((Integer) innerObj);
                            if (needExport(entry, innerProperty)) {
                                properties.add(toT3d(instantiate((UnrealPackage.ExportEntry) entry, objectFactory), objectFactory, indent));
                            }
                        }

                        fakeProperty.putAt(0, innerObj);
                        if (j > 0)
                            sb.append(newLine(indent)); //TODO
                        sb.append(property.getName()).append("(").append(j).append(")")
                                .append("=")
                                .append(inlineProperty(fakeProperty, up, objectFactory, true));
                    }
                } else if (template instanceof StructProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("[").append(i).append("]");
                    }
                    sb.append("=");
                    if (obj == null) {
                        sb.append("None");
                    } else {
                        sb.append(inlineStruct((List<L2Property>) obj, up, objectFactory));
                    }
                } else if (template instanceof StrProperty) {
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("[").append(i).append("]");
                    }
                    sb.append("=");
                    sb.append("\"").append(Objects.toString(obj)).append("\"");
                }
            }

            properties.add(sb);
        });

        return properties.build().collect(Collectors.joining(newLine(indent)));
    }

    public static CharSequence inlineProperty(L2Property property, UnrealPackage up, UnrealSerializerFactory objectFactory, boolean valueOnly) {
        StringBuilder sb = new StringBuilder();

        Property template = property.getTemplate();

        for (int i = 0; i < template.arrayDimension; i++) {
            if (!valueOnly) {
                sb.append(property.getName());

                if (template.arrayDimension > 1) {
                    sb.append("[").append(i).append("]");
                }

                sb.append("=");
            }

            java.lang.Object object = property.getAt(i);

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
                if (entry == null) {
                    sb.append("None");
                } else if (entry instanceof UnrealPackage.ImportEntry) {
                    sb.append(((UnrealPackage.ImportEntry) entry).getClassName().getName())
                            .append("'")
                            .append(entry.getObjectFullName())
                            .append("'");
                } else if (entry instanceof UnrealPackage.ExportEntry) {
                    if (Property.CPF.getFlags(template.propertyFlags).contains(Property.CPF.ExportObject)) {
                        sb.append("\"").append(entry.getObjectName().getName()).append("\"");
                    } else {
                        String clazz = "Class";
                        if (((UnrealPackage.ExportEntry) entry).getObjectClass() != null)
                            clazz = ((UnrealPackage.ExportEntry) entry).getObjectClass().getObjectName().getName();
                        sb.append(clazz)
                                .append("'")
                                .append(entry.getObjectName().getName())
                                .append("'");
                    }
                } else {
                    throw new IllegalStateException("wtf");
                }
            } else if (template instanceof NameProperty) {
                sb.append("'").append(Objects.toString(object)).append("'");
            } else if (template instanceof ArrayProperty) {
                ArrayProperty arrayProperty = (ArrayProperty) property.getTemplate();
                Property innerProperty = arrayProperty.inner;
                L2Property fakeProperty = new L2Property(innerProperty);
                List<java.lang.Object> list = (List<java.lang.Object>) object;

                sb.append(list.stream()
                        .map(o -> {
                            fakeProperty.putAt(0, o);
                            return inlineProperty(fakeProperty, up, objectFactory, true);
                        }).collect(Collectors.joining(",", "(", ")")));
            } else if (template instanceof StructProperty) {
                if (object == null) {
                    sb.append("None");
                } else {
                    sb.append(inlineStruct((List<L2Property>) object, up, objectFactory));
                }
            } else if (template instanceof StrProperty) {
                sb.append("\"").append(Objects.toString(object)).append("\"");
            }

            if (i != template.arrayDimension - 1)
                sb.append(",");
        }

        return sb;
    }

    public static CharSequence inlineStruct(List<L2Property> struct, UnrealPackage up, UnrealSerializerFactory objectFactory) {
        return struct.stream().map(p -> inlineProperty(p, up, objectFactory, false)).collect(Collectors.joining(",", "(", ")"));
    }

    public static boolean needExport(UnrealPackage.Entry entry, Property template) {
        return entry != null &&
                entry instanceof UnrealPackage.ExportEntry &&
                (Property.CPF.getFlags(template.propertyFlags).contains(Property.CPF.ExportObject) ||
                        Property.CPF.getFlags(template.propertyFlags).contains(Property.CPF.EditInlineNotify)); //FIXME

    }

    public static CharSequence toT3d(Object object, UnrealSerializerFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append("Begin Object");
        sb.append(" Class=").append(object.entry.getObjectClass().getObjectName().getName());
        sb.append(" Name=").append(object.entry.getObjectName().getName());
        sb.append(newLine(indent + 1)).append(decompileProperties(object, objectFactory, indent + 1));
        sb.append(newLine(indent)).append("End Object");

        return sb;
    }
}