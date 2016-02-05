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
import acmi.l2.clientmod.unreal.core.*;
import acmi.l2.clientmod.unreal.core.Class;
import acmi.l2.clientmod.unreal.core.Enum;
import acmi.l2.clientmod.unreal.objectfactory.ObjectFactory;

import java.util.stream.Collectors;

import static acmi.l2.clientmod.decompiler.Util.*;

public class Decompiler {
    static Field instantiate(UnrealPackageReadOnly.ExportEntry child, ObjectFactory objectFactory) {
        String objClass = child.getObjectClass() == null ? "Core.Class" : child.getObjectClass().getObjectFullName();
        if (objClass.equals("Core.Class") ||
                objClass.equals("Core.State") ||
                objClass.equals("Core.Function") ||
                objClass.equals("Core.Struct")) {
            return objectFactory.getClassLoader().getStruct(child.getObjectFullName());
        } else {
            return (Field) objectFactory.apply(child);
        }
    }

    public static CharSequence decompile(Class clazz, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        String name = clazz.getEntry().getObjectName().getName();
        String superName = clazz.getEntry().getObjectSuperClass() != null ?
                clazz.getEntry().getObjectSuperClass().getObjectName().getName() : null;

        sb.append(tab(indent)).append("class ").append(name);
        if (superName != null)
            sb.append(" extends ").append(superName);
        //TODO flags
        sb.append(";");

        if (clazz.getChild() != null) {
            sb.append(newLine(indent));
            sb.append(decompileFields(clazz, objectFactory, indent));
        }

        //TODO defaultproperties

        return sb;
    }

    public static CharSequence decompileFields(Struct struct, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        for (Field field : (Iterable<Field>) () -> new ChildIterator(struct, objectFactory)) {
            if (field instanceof Const) {
                sb.append(newLine()).append(decompileConst((Const) field, objectFactory, indent)).append(";");
            } else if (field instanceof Enum) {
                sb.append(newLine()).append(decompileEnum((Enum) field, objectFactory, indent)).append(";");
            } else if (field instanceof Property) {
                if (field instanceof DelegateProperty)
                    continue;

                sb.append(newLine()).append(decompileProperty((Property) field, struct, objectFactory, indent)).append(";");
            } else if (field instanceof State) {
                sb.append(newLine()).append(decompileState((State) field, objectFactory, indent)).append(";");
            } else if (field instanceof Function) {
                sb.append(newLine()).append(decompileFunction((Function) field, objectFactory, indent));
            } else if (field instanceof Struct) {
                sb.append(newLine()).append(decompileStruct((Struct) field, objectFactory, indent)).append(";");
            } else {
                sb.append(newLine()).append(field);
            }
        }

        return sb;
    }

    public static CharSequence decompileConst(Const c, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append(tab(indent))
                .append("const ")
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

        sb.append(tab(indent));
        sb.append("var ");
        sb.append(getType(property, objectFactory)).append(" ");
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
        sb.append(tab(indent)).append("struct ").append(struct.getEntry().getObjectName().getName());
        sb.append(newLine(indent)).append("{");
        sb.append(decompileFields(struct, objectFactory, indent + 1));
        sb.append(newLine(indent)).append("}");
        return sb;
    }

    public static CharSequence decompileFunction(Function function, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(tab(indent));
        sb.append("//function_");
        sb.append(function.getFriendlyName());

        return sb;
    }

    public static CharSequence decompileState(State state, ObjectFactory objectFactory, int indent) {
        StringBuilder sb = new StringBuilder();

        sb.append(tab(indent)).append("state ");
        sb.append(state.getEntry().getObjectName().getName());
        if (state.getEntry().getObjectSuperClass() != null) {
            sb.append(" extends ").append(state.getEntry().getObjectSuperClass().getObjectName().getName());
        }
        sb.append(newLine(indent)).append("{");
        sb.append(decompileFields(state, objectFactory, indent + 1));
        sb.append(newLine(indent)).append("}");

        return sb;
    }
}
