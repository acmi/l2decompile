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

import acmi.l2.clientmod.io.UnrealPackageReadOnly;
import acmi.l2.clientmod.unreal.classloader.L2Property;
import acmi.l2.clientmod.unreal.core.*;
import acmi.l2.clientmod.unreal.core.Class;
import acmi.l2.clientmod.unreal.core.Enum;
import acmi.l2.clientmod.unreal.core.Object;
import acmi.l2.clientmod.unreal.objectfactory.ObjectFactory;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static acmi.l2.clientmod.decompiler.Util.*;

public class Decompiler {
    static Object instantiate(UnrealPackageReadOnly.ExportEntry entry, ObjectFactory objectFactory) {
        String objClass = entry.getObjectClass() == null ? "Core.Class" : entry.getObjectClass().getObjectFullName();
        if (objClass.equals("Core.Class") ||
                objClass.equals("Core.State") ||
                objClass.equals("Core.Function") ||
                objClass.equals("Core.Struct")) {
            return objectFactory.getClassLoader().getStruct(entry.getObjectFullName());
        } else {
            return objectFactory.apply(entry);
        }
    }

    public static CharSequence decompile(Class clazz, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        String name = clazz.getEntry().getObjectName().getName();
        String superName = clazz.getEntry().getObjectSuperClass() != null ?
                clazz.getEntry().getObjectSuperClass().getObjectName().getName() : null;

        sb.append("class ").append(name);
        if (superName != null)
            sb.append(" extends ").append(superName);
        //TODO flags
        sb.append(";");

        if (clazz.getChild() != null) {
            sb.append(newLine());
            sb.append(newLine(indent)).append(decompileFields(clazz, objectFactory, indent));
        }

        if (!clazz.getProperties().isEmpty()) {
            sb.append(newLine());
            sb.append(newLine(indent)).append("defaultproperties{");
            sb.append(newLine(indent + 1)).append(decompileProperties(clazz, objectFactory, indent + 1));
            sb.append(newLine(indent)).append("}");
        }

        return sb;
    }

    public static CharSequence decompileFields(Struct struct, ObjectFactory objectFactory, int indent) {
        Stream.Builder<CharSequence> fields = Stream.builder();

        for (Field field : (Iterable<Field>) () -> new ChildIterator(struct, objectFactory)) {
            if (field instanceof Const) {
                fields.add(decompileConst((Const) field, objectFactory, indent) + ";");
            } else if (field instanceof Enum) {
                if (!struct.getClass().equals(Struct.class))
                    fields.add(decompileEnum((Enum) field, objectFactory, indent) + ";");
            } else if (field instanceof Property) {
                if (field instanceof DelegateProperty)
                    continue;

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
        }

        return fields.build().collect(Collectors.joining(newLine(indent)));
    }

    public static CharSequence decompileConst(Const c, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append("const ")
                .append(c.getEntry().getObjectName().getName())
                .append(" =")
                .append(c.constant);

        return sb;
    }

    public static CharSequence decompileEnum(Enum e, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append(tab(indent)).append("enum ").append(e.getEntry().getObjectName().getName())
                .append(newLine(indent)).append("{")
                .append(newLine(indent + 1)).append(e.getValues().stream().collect(Collectors.joining("," + newLine(indent + 1))))
                .append(newLine(indent)).append("}");

        return sb;
    }

    public static CharSequence decompileProperty(Property property, Struct parent, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        Collection<Property.CPF> propertyFlags = property.getPropertyFlags();
        Collection<UnrealPackageReadOnly.ObjectFlag> objectFlags = UnrealPackageReadOnly.ObjectFlag.getFlags(property.getEntry().getObjectFlags());

        sb.append("var");
        if (propertyFlags.contains(Property.CPF.Edit)) {
            sb.append("(").append(property.getCategory()).append(")");
        }
        sb.append(" ");
        if (propertyFlags.contains(Property.CPF.Travel)) {
            sb.append("travel ");
        }
        if (propertyFlags.contains(Property.CPF.Localized)) {
            sb.append("localized ");
        }
        if (propertyFlags.contains(Property.CPF.Transient)) {
            sb.append("transient ");
        }
        if (propertyFlags.contains(Property.CPF.Input)) {
            sb.append("input ");
        }
        if (propertyFlags.contains(Property.CPF.ExportObject)) {
            sb.append("export ");
        }
        if (propertyFlags.contains(Property.CPF.EditInline)) {
            sb.append("editinline ");
        }
        if (propertyFlags.contains(Property.CPF.EdFindable)) {
            sb.append("edfindable ");
        }
        if (propertyFlags.contains(Property.CPF.GlobalConfig)) {
            sb.append("globalconfig ");
        }
        if (propertyFlags.contains(Property.CPF.Config) && !propertyFlags.contains(Property.CPF.GlobalConfig)) {
            sb.append("config ");
        }
        if (propertyFlags.contains(Property.CPF.Native)) {
            sb.append("native ");
        }
        if (propertyFlags.contains(Property.CPF.Deprecated)) {
            sb.append("deprecated ");
        }
        if (objectFlags.contains(UnrealPackageReadOnly.ObjectFlag.Private)) {
            sb.append("private ");
        }
        if (propertyFlags.contains(Property.CPF.Const)) {
            sb.append("const ");
        }
        if (propertyFlags.contains(Property.CPF.EditConst)) {
            sb.append("editconst ");
        }
        CharSequence type = getType(property, objectFactory);
        if (parent.getClass().equals(Struct.class)) {
            if (property instanceof ByteProperty &&
                    ((ByteProperty) property).enumType != 0) {
                UnrealPackageReadOnly.Entry enumLocalEntry = ((ByteProperty) property).getEnumType();
                UnrealPackageReadOnly.ExportEntry enumEntry = objectFactory.getClassLoader()
                        .getExportEntry(enumLocalEntry.getObjectFullName(), e -> e.getObjectClass() != null && e.getObjectClass().getObjectFullName().equalsIgnoreCase("Core.Enum"));
                Enum en = (Enum) objectFactory.apply(enumEntry);
                type = decompileEnum(en, objectFactory, indent);
            }
            //FIXME array<enum>
        }
        sb.append(type).append(" ");
        sb.append(property.getEntry().getObjectName().getName());
        if (property.arrayDimension > 1)
            sb.append("[").append(property.arrayDimension).append("]");

        return sb;
    }

    public static String getType(Property property, ObjectFactory objectFactory) {
        if (property instanceof ByteProperty) {
            if (((ByteProperty) property).enumType != 0) {
                UnrealPackageReadOnly.Entry enumLocalEntry = ((ByteProperty) property).getEnumType();
                return enumLocalEntry.getObjectName().getName();
            } else {
                return "byte";
            }
        } else if (property instanceof IntProperty) {
            return "int";
        } else if (property instanceof BoolProperty) {
            return "bool";
        } else if (property instanceof FloatProperty) {
            return "float";
        } else if (property instanceof ObjectProperty) {
            return ((ObjectProperty) property).getObjectType().getObjectName().getName();
        } else if (property instanceof NameProperty) {
            return "name";
        } else if (property instanceof ArrayProperty) {
            ArrayProperty arrayProperty = (ArrayProperty) property;
            Property innerProperty = (Property) objectFactory.apply(objectFactory.getClassLoader().getExportEntry(arrayProperty.getInner().getObjectFullName(), e -> true));
            return "array<" + getType(innerProperty, objectFactory) + ">";
        } else if (property instanceof StructProperty) {
            return ((StructProperty) property).getStructType().getObjectName().getName();
        } else if (property instanceof StrProperty) {
            return "string";
        } else {
            throw new IllegalStateException(property.toString());
        }
    }

    public static CharSequence decompileStruct(Struct struct, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append("struct ").append(struct.getEntry().getObjectName().getName());
        sb.append(newLine(indent)).append("{");
        sb.append(newLine(indent + 1)).append(decompileFields(struct, objectFactory, indent + 1));
        sb.append(newLine(indent)).append("}");
        return sb;
    }

    public static CharSequence decompileFunction(Function function, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append("//function_").append(function.getFriendlyName()); //TODO

        return sb;
    }

    public static CharSequence decompileState(State state, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append("state ");
        sb.append(state.getEntry().getObjectName().getName());
        if (state.getEntry().getObjectSuperClass() != null) {
            sb.append(" extends ").append(state.getEntry().getObjectSuperClass().getObjectName().getName());
        }
        sb.append(newLine(indent)).append("{");
        sb.append(newLine(indent + 1)).append(decompileFields(state, objectFactory, indent + 1));
        sb.append(newLine(indent)).append("}");

        return sb;
    }

    public static CharSequence decompileProperties(Object object, ObjectFactory objectFactory, int indent) {
        Stream.Builder<CharSequence> properties = Stream.builder();

        UnrealPackageReadOnly up = object.getEntry().getUnrealPackage();

        object.getProperties().forEach(property -> {
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
                    if (((ByteProperty) template).enumType != 0) {
                        UnrealPackageReadOnly.Entry enumLocalEntry = ((ByteProperty) template).getEnumType();
                        UnrealPackageReadOnly.ExportEntry enumEntry = objectFactory.getClassLoader().getExportEntry(enumLocalEntry.getObjectFullName(), e -> true);
                        Enum en = (Enum) objectFactory.apply(enumEntry);
                        sb.append(en.getValues().get((Integer) obj));
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
                    UnrealPackageReadOnly.Entry entry = up.objectReference((Integer) obj);
                    if (needExport(entry, template)) {
                        properties.add(toT3d(instantiate((UnrealPackageReadOnly.ExportEntry) entry, objectFactory), objectFactory, indent));
                    }
                    sb.append(property.getName());
                    if (template.arrayDimension > 1) {
                        sb.append("[").append(i).append("]");
                    }
                    sb.append("=");
                    if (entry == null) {
                        sb.append("None");
                    } else if (entry instanceof UnrealPackageReadOnly.ImportEntry) {
                        sb.append(((UnrealPackageReadOnly.ImportEntry) entry).getClassName().getName())
                                .append("'")
                                .append(entry.getObjectFullName())
                                .append("'");
                    } else if (entry instanceof UnrealPackageReadOnly.ExportEntry) {
                        String clazz = "Class";
                        if (((UnrealPackageReadOnly.ExportEntry) entry).getObjectClass() != null)
                            clazz = ((UnrealPackageReadOnly.ExportEntry) entry).getObjectClass().getObjectName().getName();
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
                    Property innerProperty = (Property) objectFactory.apply(objectFactory.getClassLoader().getExportEntry(arrayProperty.getInner().getObjectFullName(), e -> true));
                    L2Property fakeProperty = new L2Property(innerProperty, up);
                    List<java.lang.Object> list = (List<java.lang.Object>) obj;

                    for (int j = 0; j < list.size(); j++) {
                        java.lang.Object innerObj = list.get(j);

                        if (innerProperty instanceof ObjectProperty) {
                            UnrealPackageReadOnly.Entry entry = up.objectReference((Integer) innerObj);
                            if (needExport(entry, innerProperty)) {
                                properties.add(toT3d(instantiate((UnrealPackageReadOnly.ExportEntry) entry, objectFactory), objectFactory, indent));
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

    public static CharSequence inlineProperty(L2Property property, UnrealPackageReadOnly up, ObjectFactory objectFactory, boolean valueOnly) {
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
                if (((ByteProperty) template).enumType != 0) {
                    UnrealPackageReadOnly.Entry enumLocalEntry = ((ByteProperty) template).getEnumType();
                    UnrealPackageReadOnly.ExportEntry enumEntry = objectFactory.getClassLoader().getExportEntry(enumLocalEntry.getObjectFullName(), e -> true);
                    Enum en = (Enum) objectFactory.apply(enumEntry);
                    sb.append(en.getValues().get((Integer) object));
                } else {
                    sb.append(object);
                }
            } else if (template instanceof IntProperty ||
                    template instanceof BoolProperty) {
                sb.append(object);
            } else if (template instanceof FloatProperty) {
                sb.append(String.format(Locale.US, "%f", (Float) object));
            } else if (template instanceof ObjectProperty) {
                UnrealPackageReadOnly.Entry entry = up.objectReference((Integer) object);
                if (entry == null) {
                    sb.append("None");
                } else if (entry instanceof UnrealPackageReadOnly.ImportEntry) {
                    sb.append(((UnrealPackageReadOnly.ImportEntry) entry).getClassName().getName())
                            .append("'")
                            .append(entry.getObjectFullName())
                            .append("'");
                } else if (entry instanceof UnrealPackageReadOnly.ExportEntry) {
                    if (template.getPropertyFlags().contains(Property.CPF.ExportObject)) {
                        sb.append("\"").append(entry.getObjectName().getName()).append("\"");
                    } else {
                        String clazz = "Class";
                        if (((UnrealPackageReadOnly.ExportEntry) entry).getObjectClass() != null)
                            clazz = ((UnrealPackageReadOnly.ExportEntry) entry).getObjectClass().getObjectName().getName();
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
                Property innerProperty = (Property) objectFactory.apply(objectFactory.getClassLoader().getExportEntry(arrayProperty.getInner().getObjectFullName(), e -> true));
                L2Property fakeProperty = new L2Property(innerProperty, up);
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

    public static CharSequence inlineStruct(List<L2Property> struct, UnrealPackageReadOnly up, ObjectFactory objectFactory) {
        return struct.stream().map(p -> inlineProperty(p, up, objectFactory, false)).collect(Collectors.joining(",", "(", ")"));
    }

    public static boolean needExport(UnrealPackageReadOnly.Entry entry, Property template) {
        return entry != null &&
                entry instanceof UnrealPackageReadOnly.ExportEntry &&
                (template.getPropertyFlags().contains(Property.CPF.ExportObject) ||
                        template.getPropertyFlags().contains(Property.CPF.UNK8)); //FIXME

    }

    public static CharSequence toT3d(Object object, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append("Begin Object");
        sb.append(" Class=").append(object.getEntry().getObjectClass().getObjectName().getName());
        sb.append(" Name=").append(object.getEntry().getObjectName().getName());
        sb.append(newLine(indent + 1)).append(decompileProperties(object, objectFactory, indent + 1));
        sb.append(newLine(indent)).append("End Object");

        return sb;
    }
}
