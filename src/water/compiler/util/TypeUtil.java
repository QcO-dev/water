package water.compiler.util;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.compiler.Context;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Extra utility for dealing with ASM Type objects.
 *
 * @link org.objectweb.asm.Type
 */
public class TypeUtil {

	private static final List<Integer> TYPE_SIZE = List.of(Type.DOUBLE, Type.FLOAT, Type.LONG, Type.INT, Type.SHORT, Type.CHAR, Type.BYTE);
	/** A constant defining a Type representing the java.lang.String class */
	public static final Type STRING_TYPE = Type.getObjectType("java/lang/String");

	/** Returns the correct opcode given a type. */
	public static int getPopOpcode(Type type) {
		if(type.getSize() == 2) return Opcodes.POP2;
		else if(type.getSize() == 1) return Opcodes.POP;
		return -1;
	}

	/** Returns the correct opcode given a type. */
	public static int getDupOpcode(Type type) {
		if(type.getSize() == 2) return Opcodes.DUP2;
		else if(type.getSize() == 1) return Opcodes.DUP;
		return -1;
	}

	/**
	 *
	 * Converts a type to the class of same type.
	 *
	 * Method types should not be passed to this method -> resulting in an IllegalArgumentException
	 *
	 * @param type The type to convert
	 * @param context The current compiler context - used for resolving the class loader
	 * @return The Class representing the same type as the Type
	 * @throws ClassNotFoundException Given an object type, or array type of objects, if the class cannot be found
	 */
	public static Class<?> typeToClass(Type type, Context context) throws ClassNotFoundException {
		return switch (type.getSort()) {
			case Type.BOOLEAN -> boolean.class;
			case Type.CHAR -> char.class;
			case Type.BYTE -> byte.class;
			case Type.SHORT -> short.class;
			case Type.INT -> int.class;
			case Type.FLOAT -> float.class;
			case Type.LONG -> long.class;
			case Type.DOUBLE -> double.class;
			case Type.ARRAY -> typeToClass(type.getElementType(), context).arrayType();
			case Type.OBJECT -> Class.forName(type.getClassName(), false, context.getLoader());
			default -> throw new IllegalArgumentException("Unexpected value: " + type.getSort());
		};
	}

	/**
	 * If the type is primitive - not an object, array, or method
	 *
	 * @param type The type to check
	 * @return If the type is of a primitive value
	 */
	public static boolean isPrimitive(Type type) {
		return type.getSort() != Type.OBJECT &&
				type.getSort() != Type.ARRAY &&
				type.getSort() != Type.METHOD;
	}

	/**
	 * If the type represents an integer type - including long.
	 * @param type The type to check
	 * @return If the type is an integer type
	 */
	public static boolean isInteger(Type type) {
		return isPrimitive(type) && (
					type.getSort() == Type.INT ||
					type.getSort() == Type.BYTE ||
					type.getSort() == Type.CHAR ||
					type.getSort() == Type.SHORT ||
					type.getSort() == Type.LONG
				);
	}

	/**
	 *
	 * Returns the 'larger' of the type types - the one with greater precision.
	 *
	 * The sequence is as follows: <br/>
	 * double, float, long, int, short, char, byte
	 *
	 * @param left The first type to compare
	 * @param right The second type to compare
	 * @return The larger / more precise type
	 */
	public static Type getLarger(Type left, Type right) {
		if(TYPE_SIZE.indexOf(left.getSort()) < TYPE_SIZE.indexOf(right.getSort())) return left;
		else if(TYPE_SIZE.indexOf(left.getSort()) > TYPE_SIZE.indexOf(right.getSort())) return right;
		return left; // The same
	}

	/**
	 *
	 * Returns if the type given is a floating point type.
	 *
	 * This includes double and float.
	 *
	 * @param type The type to test
	 * @return If the type represents a floating point type.
	 */
	public static boolean isFloat(Type type) {
		return type.getSort() == Type.DOUBLE ||
				type.getSort() == Type.FLOAT;
	}

	/**
	 * Returns if the type is numeric - an integer type or a float type.
	 *
	 * @param type The type to test
	 * @return If the type is numeric
	 */
	public static boolean isNumeric(Type type) {
		return isInteger(type) || isFloat(type);
	}

	/**
	 * Converts a type to a String.
	 * This String representation differs from Type.toString()
	 * as it gives a standard String instead of a descriptor.
	 *
	 * E.g.
	 * Type.BOOLEAN_TYPE
	 * -> "boolean"
	 * instead of "Z"
	 *
	 * @param type The type to convert
	 * @return The String representation
	 */
	public static String stringify(Type type) {
		return switch (type.getSort()) {
			case Type.VOID -> "void";
			case Type.BOOLEAN -> "boolean";
			case Type.CHAR -> "char";
			case Type.BYTE -> "byte";
			case Type.SHORT -> "short";
			case Type.INT -> "int";
			case Type.FLOAT -> "float";
			case Type.LONG -> "long";
			case Type.DOUBLE -> "double";
			case Type.ARRAY -> stringify(type.getElementType()) + "[]";
			case Type.OBJECT -> type.getClassName();
			case Type.METHOD -> "method"; // Should not be reached
			default -> null; // Unreachable
		};
	}

	// https://stackoverflow.com/questions/11340330/java-bytecode-swap-for-double-and-long-values - accepted answer
	/**
	 *
	 * Generates bytecode to swap the two top values.
	 * This bytecode differs based on the size of the types.
	 *
	 * @param mv The MethodVisitor to append the instructions to
	 * @param stackTop The top value type
	 * @param belowTop The bottom value type
	 */
	public static void swap(MethodVisitor mv, Type stackTop, Type belowTop) {
		if (stackTop.getSize() == 1) {
			if (belowTop.getSize() == 1) {
				// Top = 1, below = 1
				mv.visitInsn(Opcodes.SWAP);
			} else {
				// Top = 1, below = 2
				mv.visitInsn(Opcodes.DUP_X2);
				mv.visitInsn(Opcodes.POP);
			}
		} else {
			if (belowTop.getSize() == 1) {
				// Top = 2, below = 1
				mv.visitInsn(Opcodes.DUP2_X1);
			} else {
				// Top = 2, below = 2
				mv.visitInsn(Opcodes.DUP2_X2);
			}
			mv.visitInsn(Opcodes.POP2);
		}
	}

	/**
	 *
	 * Returns an opcode to generate a 'dummy constant'.
	 * This is a value which is used for bytecode verification expecting a value,
	 * when the actual value is not known.
	 *
	 * @param type The type to create a dummy value for
	 * @return The opcode of the dummy constant
	 */
	public static int dummyConstant(Type type) {
		return switch (type.getSort()) {
			case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> Opcodes.ICONST_0;
			case Type.LONG -> Opcodes.LCONST_0;
			case Type.FLOAT -> Opcodes.FCONST_0;
			case Type.DOUBLE -> Opcodes.DCONST_0;
			default -> Opcodes.ACONST_NULL;
		};
	}

	/**
	 *
	 * Tests if two types can be implicitly cast.
	 * For example:
	 * int -> double returns true <br/>
	 * double -> int returns false <br/>
	 * Due to loss of precision
	 *
	 * @param to The type to attempt to cast to
	 * @param from The type to attempt to cast from
	 * @param context The current compiler context
	 * @param convert If bytecode should be generated for conversions (such as i2d)
	 * @return If the types can be cast implicitly
	 * @throws ClassNotFoundException If either type represents a class which cannot be resolved
	 */
	public static boolean isAssignableFrom(Type to, Type from, Context context, boolean convert) throws ClassNotFoundException {
		if(to.getSort() == Type.OBJECT && from.getSort() == Type.OBJECT) {
			return typeToClass(to, context).isAssignableFrom(typeToClass(from, context));
		}

		else if(isPrimitive(to) && isPrimitive(from)) {
			if(TYPE_SIZE.indexOf(to.getSort()) <= TYPE_SIZE.indexOf(from.getSort())) {
				if(convert) cast(context.getMethodVisitor(), from, to);
				return true;
			}
			return false;
		}

		//TODO Auto-boxing and arrays

		return false;
	}

	/**
	 * Gets the appropriate compare opcode for types double, float, and long.
	 * Adds this opcode to the method visitor.
	 * @param mv The method visitor to use.
	 * @param type The type to generate opcode for - only types double, float, and long are not no-ops.
	 */
	public static void compareInit(MethodVisitor mv, Type type) {
		switch (type.getSort()) {
			case Type.DOUBLE -> mv.visitInsn(Opcodes.DCMPL);
			case Type.FLOAT -> mv.visitInsn(Opcodes.FCMPL);
			case Type.LONG -> mv.visitInsn(Opcodes.LCMP);
		}
	}

	/**
	 * Creates the optimal integer opcode for the given value.
	 * @param val The integer to add to bytecode
	 * @param context The context, used for its {@link Context#getMethodVisitor()}  method
	 */
	public static void generateCorrectInt(int val, Context context) {
		MethodVisitor method = context.getMethodVisitor();

		// Handle all positive cases
		if(val >= 0) {
			// 0-5
			if(val <= 5) {
				method.visitInsn(switch(val) {
					case 0 -> Opcodes.ICONST_0;
					case 1 -> Opcodes.ICONST_1;
					case 2 -> Opcodes.ICONST_2;
					case 3 -> Opcodes.ICONST_3;
					case 4 -> Opcodes.ICONST_4;
					case 5 -> Opcodes.ICONST_5;
					default -> 0;
				});
			}
			// 1 byte integers / chars
			else if(val < 128) method.visitIntInsn(Opcodes.BIPUSH, val);
				// 2 byte integers / shorts
			else if(val < 32768) method.visitIntInsn(Opcodes.SIPUSH, val);
				// Integers stored within the constant pool
			else method.visitLdcInsn(val);
		}
		else {
			if(val == -1) method.visitInsn(Opcodes.ICONST_M1);
			else if(val >= -128) method.visitIntInsn(Opcodes.BIPUSH, val);
			else if(val >= -32768) method.visitIntInsn(Opcodes.SIPUSH, val);
			else method.visitLdcInsn(val);
		}
	}

	/**
	 * Creates the optimal opcode for the given double.
	 * @param val The double to generate bytecode for.
	 * @param context The context of the method.
	 */
	public static void generateCorrectDouble(double val, Context context) {
		MethodVisitor method = context.getMethodVisitor();

		if (val == 0) method.visitInsn(Opcodes.DCONST_0);
		else if (val == 1) method.visitInsn(Opcodes.DCONST_1);
		else method.visitLdcInsn(val);
	}

	/**
	 * Given an object, creates the optimal opcode to load it in bytecode (where possible).
	 * @param object The object to add to bytecode.
	 * @param context The context of the method.
	 */
	public static void correctLdc(Object object, Context context) {
		//TODO Other primitive constants
		if(object == null) {
			context.getMethodVisitor().visitInsn(Opcodes.ACONST_NULL);
		}
		else if(object instanceof Integer) {
			generateCorrectInt(((Integer) object).intValue(), context);
		}
		else if(object instanceof Double) {
			generateCorrectDouble(((Double) object).doubleValue(), context);
		}
		else if(object instanceof Boolean) {
			context.getMethodVisitor().visitInsn(((Boolean) object).booleanValue() ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
		}
		else {
			context.getMethodVisitor().visitLdcInsn(object);
		}
	}

	/**
	 * Auto-boxes a type to its Object wrapper.
	 * Adds the correct bytecode to the method visitor.
	 * @param mv The method visitor.
	 * @param type The (primitive) type to auto-box
	 * @return The auto-boxed object type.
	 */
	public static Type autoBox(MethodVisitor mv, Type type) {
		if(!isPrimitive(type)) return type;

		Type wrapper = getAutoBoxWrapper(type);

		autoBox(mv, wrapper, type);

		return wrapper;
	}

	/**
	 * Gets the auto-boxed Object type for a given type.
	 * @param type The type to auto-box.
	 * @return The Object wrapper type.
	 */
	public static Type getAutoBoxWrapper(Type type) {
		if(!isPrimitive(type)) return type;

		String internalName = switch (type.getSort()) {
			case Type.BOOLEAN -> "java/lang/Boolean";
			case Type.BYTE -> "java/lang/Byte";
			case Type.CHAR -> "java/lang/Character";
			case Type.FLOAT -> "java/lang/Float";
			case Type.INT -> "java/lang/Integer";
			case Type.LONG -> "java/lang/Long";
			case Type.SHORT -> "java/lang/Short";
			case Type.DOUBLE -> "java/lang/Double";
			default -> null;
		};

		return Type.getObjectType(internalName);
	}

	/**
	 * Generates the bytecode to auto-box.
	 * @param mv The method visitor.
	 * @param wrapper The type to wrap to.
	 * @param type The type to wrap from.
	 */
	private static void autoBox(MethodVisitor mv, Type wrapper, Type type) {
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper.getInternalName(), "valueOf", "(%s)%s".formatted(type.getDescriptor(), wrapper.getDescriptor()), false);
	}

	/**
	 * Returns the correct opcode to invoke a given method.
	 * @param m The method to invoke
	 * @return The correct opcode
	 */
	public static int getInvokeOpcode(Method m) {
		return Modifier.isStatic(m.getModifiers()) ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL;
	}

	/**
	 * Returns the correct opcode to access a field.
	 * @param f The field to access
	 * @return The correct opcode
	 */
	public static int getAccessOpcode(Field f) {
		return Modifier.isStatic(f.getModifiers()) ? Opcodes.GETSTATIC : Opcodes.GETFIELD;
	}

	/**
	 * Returns the correct opcode to set a field.
	 * @param f The field to set
	 * @return The correct opcode
	 */
	public static int getMemberPutOpcode(Field f) {
		return Modifier.isStatic(f.getModifiers()) ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD;
	}

	public static int getDupX1Opcode(Type type) {
		return type.getSize() == 2 ? Opcodes.DUP2_X1 : Opcodes.DUP_X1;
	}

	/*
	 Copyright Notice for use of ASM code (if link dies):

	 ASM: a very small and fast Java bytecode manipulation framework
	 Copyright (c) 2000-2011 INRIA, France Telecom
	 All rights reserved.

	 Redistribution and use in source and binary forms, with or without
	 modification, are permitted provided that the following conditions
	 are met:
	 1. Redistributions of source code must retain the above copyright
		notice, this list of conditions and the following disclaimer.
	 2. Redistributions in binary form must reproduce the above copyright
		notice, this list of conditions and the following disclaimer in the
		documentation and/or other materials provided with the distribution.
	 3. Neither the name of the copyright holders nor the names of its
		contributors may be used to endorse or promote products derived from
		this software without specific prior written permission.

	 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
	 AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
	 IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
	 ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
	 LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
	 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
	 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
	 INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
	 CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
	 ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
	 THE POSSIBILITY OF SUCH DAMAGE.

	 */
	/**
	 * Generates the correct bytecode for a cast (of primitives)
	 *
	 * Taken from ASM Commons InstructionAdapter
	 * <a href="https://github.com/llbit/ow2-asm/blob/master/src/org/objectweb/asm/commons/InstructionAdapter.java">Github</a>
	 *
	 * <a href="https://github.com/llbit/ow2-asm/blob/master/LICENSE.txt">Copyright Notice</a>
	 *
	 * @param from The type to cast from
	 * @param to The type to cast to
	 */
	public static void cast(MethodVisitor mv, final Type from, final Type to) {
		if (from != to) {
			if (from == Type.DOUBLE_TYPE) {
				if (to == Type.FLOAT_TYPE) {
					mv.visitInsn(Opcodes.D2F);
				} else if (to == Type.LONG_TYPE) {
					mv.visitInsn(Opcodes.D2L);
				} else {
					mv.visitInsn(Opcodes.D2I);
					cast(mv, Type.INT_TYPE, to);
				}
			} else if (from == Type.FLOAT_TYPE) {
				if (to == Type.DOUBLE_TYPE) {
					mv.visitInsn(Opcodes.F2D);
				} else if (to == Type.LONG_TYPE) {
					mv.visitInsn(Opcodes.F2L);
				} else {
					mv.visitInsn(Opcodes.F2I);
					cast(mv, Type.INT_TYPE, to);
				}
			} else if (from == Type.LONG_TYPE) {
				if (to == Type.DOUBLE_TYPE) {
					mv.visitInsn(Opcodes.L2D);
				} else if (to == Type.FLOAT_TYPE) {
					mv.visitInsn(Opcodes.L2F);
				} else {
					mv.visitInsn(Opcodes.L2I);
					cast(mv, Type.INT_TYPE, to);
				}
			} else {
				if (to == Type.BYTE_TYPE) {
					mv.visitInsn(Opcodes.I2B);
				} else if (to == Type.CHAR_TYPE) {
					mv.visitInsn(Opcodes.I2C);
				} else if (to == Type.DOUBLE_TYPE) {
					mv.visitInsn(Opcodes.I2D);
				} else if (to == Type.FLOAT_TYPE) {
					mv.visitInsn(Opcodes.I2F);
				} else if (to == Type.LONG_TYPE) {
					mv.visitInsn(Opcodes.I2L);
				} else if (to == Type.SHORT_TYPE) {
					mv.visitInsn(Opcodes.I2S);
				}
			}
		}
	}

}
