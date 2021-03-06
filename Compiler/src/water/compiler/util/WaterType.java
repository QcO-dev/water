package water.compiler.util;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.runtime.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class WaterType {
	private static final List<Integer> TYPE_SIZE = List.of(Type.DOUBLE, Type.FLOAT, Type.LONG, Type.INT, Type.SHORT, Type.CHAR, Type.BYTE);
	/* constant types for utility */
	public static WaterType BOOLEAN_TYPE = new WaterType(Type.BOOLEAN_TYPE);
	public static WaterType BYTE_TYPE = new WaterType(Type.BYTE_TYPE);
	public static WaterType CHAR_TYPE = new WaterType(Type.CHAR_TYPE);
	public static WaterType DOUBLE_TYPE = new WaterType(Type.DOUBLE_TYPE);
	public static WaterType FLOAT_TYPE = new WaterType(Type.FLOAT_TYPE);
	public static WaterType INT_TYPE = new WaterType(Type.INT_TYPE);
	public static WaterType LONG_TYPE = new WaterType(Type.LONG_TYPE);
	public static WaterType SHORT_TYPE = new WaterType(Type.SHORT_TYPE);
	public static WaterType VOID_TYPE = new WaterType(Type.VOID_TYPE);
	public static WaterType NULL_TYPE = WaterType.getObjectType("java/lang/Object");
	/** A constant defining a Type representing the java.lang.String class */
	public static final WaterType STRING_TYPE = WaterType.getObjectType("java/lang/String");
	/** A constant defining a Type representing the java.lang.Object class */
	public static final WaterType OBJECT_TYPE = WaterType.getObjectType("java/lang/Object");
	/** A constant defining a Type representing the java.lang.Object class, which is nullable (java.lang.Object?) */
	public static final WaterType NULLABLE_OBJECT_TYPE = OBJECT_TYPE.asNullable();

	static {
		NULL_TYPE.isNullable = true;
		NULL_TYPE.sort = Sort.NULL;
	}

	public enum Sort {
		VOID,
		BOOLEAN,
		CHAR,
		BYTE,
		SHORT,
		INT,
		FLOAT,
		LONG,
		DOUBLE,
		ARRAY,
		OBJECT,
		METHOD,
		NULL
	}


	private Type asmType;
	private Sort sort;
	private boolean isNullable;
	private WaterType returnType;
	private WaterType[] argumentTypes;
	private WaterType elementType;
	private List<Integer> nullableDimensions;

	public WaterType(Type asmType) {
		this.asmType = asmType;
		sort = Sort.values()[asmType.getSort()];
		this.isNullable = false;
		this.elementType = asmType.getSort() == Type.ARRAY ? new WaterType(asmType.getElementType()) : null;
	}

	public WaterType(Sort sort) {
		this.asmType = null;
		this.sort = sort;
		this.isNullable = false;
	}

	/**
	 * If the type is primitive - not an object, array, or method
	 *
	 * @return If the type is of a primitive value
	 */
	public boolean isPrimitive() {
		return  getSort() != Sort.OBJECT &&
				getSort() != Sort.ARRAY &&
				getSort() != Sort.METHOD &&
				getSort() != Sort.NULL;
	}

	/**
	 *
	 * Converts a type to the class of same type.
	 *
	 * Method types should not be passed to this method -> resulting in an IllegalArgumentException
	 *
	 * @param context The current compiler context - used for resolving the class loader
	 * @return The Class representing the same type as the Type
	 * @throws ClassNotFoundException Given an object type, or array type of objects, if the class cannot be found
	 */
	public Class<?> toClass(Context context) throws ClassNotFoundException {
		return switch (asmType.getSort()) {
			case Type.BOOLEAN -> boolean.class;
			case Type.CHAR -> char.class;
			case Type.BYTE -> byte.class;
			case Type.SHORT -> short.class;
			case Type.INT -> int.class;
			case Type.FLOAT -> float.class;
			case Type.LONG -> long.class;
			case Type.DOUBLE -> double.class;
			case Type.ARRAY -> getElementType().toClass(context).arrayType();
			case Type.OBJECT -> Class.forName(getClassName(), false, context.getLoader());
			default -> throw new IllegalArgumentException("Unexpected value: " + asmType.getSort());
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
	 * @param from The type to attempt to cast from
	 * @param context The current compiler context
	 * @param convert If bytecode should be generated for conversions (such as i2d)
	 * @return If the types can be cast implicitly
	 * @throws ClassNotFoundException If either type represents a class which cannot be resolved
	 */
	public boolean isAssignableFrom(WaterType from, Context context, boolean convert) throws ClassNotFoundException {
		// Covers assigning null to both arrays and objects.
		if(isNullable() && (isArray() || isObject()) && from.isNull()) {
			return true;
		}
		if(isObject() && from.isObject()) {
			if(!isNullable() && from.isNullable()) return false;
			return toClass(context).isAssignableFrom(from.toClass(context));
		}

		else if(isPrimitive() && from.isPrimitive()) {
			if(TYPE_SIZE.indexOf(asmType.getSort()) <= TYPE_SIZE.indexOf(from.getRawType().getSort())) {
				if(convert) from.cast(this, context.getMethodVisitor());
				return true;
			}
			if(isRepresentedAsInteger() && from.isRepresentedAsInteger()) {
				if(convert) from.cast(this, context.getMethodVisitor());
				return true;
			}
			return false;
		}

		if(isArray() && from.isArray()) {
			if(!isNullable() && from.isNullable()) return false;
			if(getElementType().isNullable()) {
				return getElementType().equals(from.getElementType().asNullable());
			}
			return getElementType().equals(from.getElementType());
		}

		//TODO Auto-boxing

		return false;
	}

	/**
	 * Returns how many changes / how simple the conversion between types is. Assumes the types can be cast.
	 * @param from The type to cast from
	 * * @return The complexity of the change
	 */
	public int assignChangesFrom(WaterType from) {
		if(from.equals(this) || from.isNull()) {
			return 0;
		}
		if(isObject() && from.isObject()) {
			return 1;
		}

		else if(isPrimitive() && from.isPrimitive()) {
			if(TYPE_SIZE.indexOf(getRawType().getSort()) <= TYPE_SIZE.indexOf(from.getRawType().getSort())) {
				return 1;
			}
			if(isRepresentedAsInteger() && from.isRepresentedAsInteger()) {
				return 2;
			}
		}

		if(isArray() && from.isArray()) {
			return 0;
		}

		//TODO Auto-boxing

		return -1;
	}

	/**
	 * Auto-boxes a type to its Object wrapper.
	 * Adds the correct bytecode to the method visitor.
	 * @param mv The method visitor.
	 * @return The auto-boxed object type.
	 */
	public WaterType autoBox(MethodVisitor mv) {
		if(!isPrimitive()) return this;

		WaterType wrapper = getAutoBoxWrapper();

		autoBox(mv, wrapper);

		return wrapper;
	}

	/**
	 * Gets the auto-boxed Object type for a given type.
	 * @return The Object wrapper type.
	 */
	public WaterType getAutoBoxWrapper() {
		if(!isPrimitive()) return this;

		String internalName = switch (asmType.getSort()) {
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

		return WaterType.getObjectType(internalName);
	}

	/**
	 * Generates the bytecode to auto-box.
	 * @param mv The method visitor.
	 * @param wrapper The type to wrap to.
	 */
	private void autoBox(MethodVisitor mv, WaterType wrapper) {
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper.getInternalName(), "valueOf", "(%s)%s".formatted(getDescriptor(), wrapper.getDescriptor()), false);
	}

	/**
	 * Converts a primitive type to the {@link org.objectweb.asm.Opcodes}.T_TYPE integer constant, for use with integer instructions.
	 * @return The integer encoding of the type
	 */
	public int primitiveToTType() {
		if(!isPrimitive()) return -1;

		return switch (asmType.getSort()) {
			case Type.BOOLEAN -> Opcodes.T_BOOLEAN;
			case Type.CHAR -> Opcodes.T_CHAR;
			case Type.BYTE -> Opcodes.T_BYTE;
			case Type.SHORT -> Opcodes.T_SHORT;
			case Type.INT -> Opcodes.T_INT;
			case Type.LONG -> Opcodes.T_LONG;
			case Type.FLOAT -> Opcodes.T_FLOAT;
			case Type.DOUBLE -> Opcodes.T_DOUBLE;
			default -> 0;
		};
	}

	/**
	 *
	 * Returns an opcode to generate a 'dummy constant'.
	 * This is a value which is used for bytecode verification expecting a value,
	 * when the actual value is not known.
	 *
	 * @return The opcode of the dummy constant
	 */
	public int dummyConstant() {
		return switch (asmType.getSort()) {
			case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> Opcodes.ICONST_0;
			case Type.LONG -> Opcodes.LCONST_0;
			case Type.FLOAT -> Opcodes.FCONST_0;
			case Type.DOUBLE -> Opcodes.DCONST_0;
			default -> Opcodes.ACONST_NULL;
		};
	}

	/**
	 *
	 * Generates bytecode to swap the two top values.
	 * This bytecode differs based on the size of the types.
	 *
	 * @param mv The MethodVisitor to append the instructions to
	 * @param belowTop The bottom value type
	 */
	public void swap(WaterType belowTop, MethodVisitor mv) {
		if (getSize() == 1) {
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
	 * Generates the correct bytecode for a cast (of primitives)
	 *
	 * Taken from ASM Commons InstructionAdapter
	 * <a href="https://github.com/llbit/ow2-asm/blob/master/src/org/objectweb/asm/commons/InstructionAdapter.java">Github</a>
	 *
	 * <a href="https://github.com/llbit/ow2-asm/blob/master/LICENSE.txt">Copyright Notice</a>
	 *
	 * @param to The type to cast to
	 */
	public void cast(final WaterType to, MethodVisitor mv) {
		if (!equals(to)) {
			if (equals(WaterType.DOUBLE_TYPE)) {
				if (to.equals(WaterType.FLOAT_TYPE)) {
					mv.visitInsn(Opcodes.D2F);
				} else if (to.equals(WaterType.LONG_TYPE)) {
					mv.visitInsn(Opcodes.D2L);
				} else {
					mv.visitInsn(Opcodes.D2I);
					WaterType.INT_TYPE.cast(to, mv);
				}
			} else if (equals(WaterType.FLOAT_TYPE)) {
				if (to.equals(WaterType.DOUBLE_TYPE)) {
					mv.visitInsn(Opcodes.F2D);
				} else if (to.equals(WaterType.LONG_TYPE)) {
					mv.visitInsn(Opcodes.F2L);
				} else {
					mv.visitInsn(Opcodes.F2I);
					WaterType.INT_TYPE.cast(to, mv);
				}
			} else if (equals(WaterType.LONG_TYPE)) {
				if (to.equals(WaterType.DOUBLE_TYPE)) {
					mv.visitInsn(Opcodes.L2D);
				} else if (to.equals(WaterType.FLOAT_TYPE)) {
					mv.visitInsn(Opcodes.L2F);
				} else {
					mv.visitInsn(Opcodes.L2I);
					WaterType.INT_TYPE.cast(to, mv);
				}
			} else {
				if (to.equals(WaterType.BYTE_TYPE)) {
					mv.visitInsn(Opcodes.I2B);
				} else if (to.equals(WaterType.CHAR_TYPE)) {
					mv.visitInsn(Opcodes.I2C);
				} else if (to.equals(WaterType.DOUBLE_TYPE)) {
					mv.visitInsn(Opcodes.I2D);
				} else if (to.equals(WaterType.FLOAT_TYPE)) {
					mv.visitInsn(Opcodes.I2F);
				} else if (to.equals(WaterType.LONG_TYPE)) {
					mv.visitInsn(Opcodes.I2L);
				} else if (to.equals(WaterType.SHORT_TYPE)) {
					mv.visitInsn(Opcodes.I2S);
				}
			}
		}
	}

	/** Returns the correct opcode given a type. */
	public int getPopOpcode() {
		if(getSize() == 2) return Opcodes.POP2;
		else if(getSize() == 1) return Opcodes.POP;
		return -1;
	}

	/** Returns the correct opcode given a type. */
	public int getDupOpcode() {
		if(getSize() == 2) return Opcodes.DUP2;
		else if(getSize() == 1) return Opcodes.DUP;
		return -1;
	}

	/**
	 * Returns the correct opcode for DUPY_X1 where Y is either 2 or not present, based on type size.
	 * @return The correct opcode
	 */
	public int getDupX1Opcode() {
		return getSize() == 2 ? Opcodes.DUP2_X1 : Opcodes.DUP_X1;
	}

	/**
	 * Returns the correct opcode for DUPY_X2 where Y is either 2 or not present, based on type size.
	 * @return The correct opcode
	 */
	public int getDupX2Opcode() {
		return getSize() == 2 ? Opcodes.DUP2_X2 : Opcodes.DUP_X2;
	}

	/**
	 * If the type represents an integer type - including long.
	 * @return If the type is an integer type
	 */
	public boolean isInteger() {
		return isPrimitive() && (
						asmType.getSort() == Type.INT ||
						asmType.getSort() == Type.BYTE ||
						asmType.getSort() == Type.CHAR ||
						asmType.getSort() == Type.SHORT ||
						asmType.getSort() == Type.LONG ||
						asmType.getSort() == Type.BOOLEAN
		);
	}
	/**
	 * If the type represents an integer type - excluding long.
	 * @return If the type is an integer type
	 */
	public boolean isRepresentedAsInteger() {
		return isPrimitive() && (
				asmType.getSort() == Type.INT ||
						asmType.getSort() == Type.BYTE ||
						asmType.getSort() == Type.CHAR ||
						asmType.getSort() == Type.SHORT ||
						asmType.getSort() == Type.BOOLEAN
		);
	}

	/**
	 *
	 * Returns if the type given is a floating point type.
	 *
	 * This includes double and float.
	 *
	 * @return If the type represents a floating point type.
	 */
	public boolean isFloat() {
		return asmType.getSort() == Type.DOUBLE ||
				asmType.getSort() == Type.FLOAT;
	}

	/**
	 * Returns if the type is numeric - an integer type or a float type.
	 *
	 * @return If the type is numeric
	 */
	public boolean isNumeric() {
		return isInteger() || isFloat();
	}

	public void generateAsInteger(int value, Context context) {
		switch (sort) {
			case LONG -> TypeUtil.generateCorrectLong(value, context);
			case DOUBLE -> TypeUtil.generateCorrectDouble(value, context);
			case FLOAT -> TypeUtil.generateCorrectFloat(value, context);
			default -> TypeUtil.generateCorrectInt(value, context);
		}
	}

	/**
	 *
	 * Returns the 'larger' of the type types - the one with greater precision.
	 *
	 * The sequence is as follows: <br/>
	 * double, float, long, int, short, char, byte
	 *
	 * @param right The second type to compare
	 * @return The larger / more precise type
	 */
	public WaterType getLarger(WaterType right) {
		if(TYPE_SIZE.indexOf(asmType.getSort()) < TYPE_SIZE.indexOf(right.getRawType().getSort())) return this;
		else if(TYPE_SIZE.indexOf(asmType.getSort()) > TYPE_SIZE.indexOf(right.getRawType().getSort())) return right;
		return this; // The same
	}

	/**
	 * Gets the appropriate compare opcode for types double, float, and long.
	 * Adds this opcode to the method visitor.
	 * @param mv The method visitor to use.
	 */
	public void compareInit(MethodVisitor mv) {
		switch (asmType.getSort()) {
			case Type.DOUBLE -> mv.visitInsn(Opcodes.DCMPL);
			case Type.FLOAT -> mv.visitInsn(Opcodes.FCMPL);
			case Type.LONG -> mv.visitInsn(Opcodes.LCMP);
		}
	}

	public WaterType getRootElementType() {
		if(sort != Sort.ARRAY) {
			return this;
		}
		WaterType element = getElementType();
		while(element.isArray()) {
			element = element.getElementType();
		}
		return element;
	}

	public boolean needsDimensionAnnotation() {
		return sort == Sort.ARRAY && nullableDimensions != null;
	}

	public void writeAnnotationDimensions(AnnotationVisitor visitor) {
		if(sort != Sort.ARRAY) return;

		WaterType elementType = getRootElementType();

		if(nullableDimensions == null) {
			List<Integer> dimensions = new ArrayList<>();
			if(isNullable) dimensions.add(-1);
			if(elementType.isNullable()) dimensions.add(-2);
			if (isNullable)
				visitor.visit("d", dimensions.stream().mapToInt(i -> i).toArray());
			return;
		}

		List<Integer> dimensions = new ArrayList<>(nullableDimensions);
		if(isNullable) dimensions.add(-1);
		if(elementType.isNullable()) dimensions.add(-2);

		visitor.visit("d", dimensions.stream().mapToInt(i -> i).toArray());
	}

	public WaterType asNullable() {
		WaterType type = copy();
		type.isNullable = true;
		return type;
	}

	public WaterType asNullable(boolean isNullable) {
		WaterType type = copy();
		type.isNullable = isNullable;
		return type;
	}

	public WaterType asNonNullable() {
		WaterType type = copy();
		type.isNullable = false;
		return type;
	}

	public boolean isObject() {
		return getSort() == Sort.OBJECT;
	}

	public boolean isArray() {
		return getSort() == Sort.ARRAY;
	}

	public boolean isNull() {
		return sort == Sort.NULL;
	}

	public String getInternalName() {
		return asmType.getInternalName();
	}

	public String getClassName() {
		return asmType.getClassName();
	}

	public WaterType getElementType() {
		return elementType;
	}

	public int getOpcode(int opcode) {
		return asmType.getOpcode(opcode);
	}

	public int getSize() {
		return asmType.getSize();
	}

	public WaterType[] getArgumentTypes() {
		return argumentTypes;
	}

	public WaterType getReturnType() {
		return returnType;
	}

	public String getDescriptor() {
		if(sort == Sort.METHOD) {
			StringBuilder builder = new StringBuilder("(");
			for(WaterType type : argumentTypes) {
				builder.append(type.getDescriptor());
			}
			return builder.append(")").append(returnType.getDescriptor()).toString();
		}
		return asmType.getDescriptor();
	}

	public Sort getSort() {
		return sort;
	}

	public boolean isNullable() {
		return isNullable;
	}

	private Type getRawType() {
		return asmType;
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof WaterType)) {
			return false;
		}
		WaterType other = (WaterType) obj;
		if(isNullable() != other.isNullable()) return false;
		return other.getRawType().equals(this.asmType);
	}

	@Override
	public int hashCode() {
		return asmType.hashCode();
	}

	public WaterType copy() {
		WaterType type = new WaterType(sort);
		type.asmType = asmType;
		type.sort = sort;
		type.isNullable = isNullable;
		type.returnType = returnType;
		type.argumentTypes = argumentTypes;
		type.elementType = elementType;
		type.nullableDimensions = nullableDimensions;
		return type;
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
	 * @return The String representation
	 */
	public String toString() {
		String base = switch (getSort()) {
			case VOID -> "void";
			case BOOLEAN -> "boolean";
			case CHAR -> "char";
			case BYTE -> "byte";
			case SHORT -> "short";
			case INT -> "int";
			case FLOAT -> "float";
			case LONG -> "long";
			case DOUBLE -> "double";
			case ARRAY -> getElementType() + "[]";
			case OBJECT -> asmType.getClassName();
			case METHOD -> "method"; // Should not be reached
			case NULL -> "null";
		};
		if(isNullable) base += "?";
		return base;
	}

	public static WaterType getMethodType(String descriptor) {
		WaterType type = new WaterType(Type.getMethodType(descriptor));
		type.argumentTypes = Arrays.stream(type.getRawType().getArgumentTypes()).map(WaterType::new).toArray(WaterType[]::new);
		type.returnType = new WaterType(type.getRawType().getReturnType());
		return type;
	}

	public static WaterType getMethodType(WaterType returnType, WaterType... parameterTypes) {
		WaterType type = new WaterType(Sort.METHOD);
		type.returnType = returnType;
		type.argumentTypes = parameterTypes;
		return type;
	}

	public static WaterType getArrayType(WaterType elementType, int dimensions, List<Integer> nullableDimensions) {
		if(dimensions > 1) {
			elementType = getArrayType(elementType, dimensions - 1, nullableDimensions);
			if(nullableDimensions != null && nullableDimensions.contains(dimensions - 1)) elementType.isNullable = true;
		}
		WaterType type = new WaterType(Sort.ARRAY);
		type.elementType = elementType;
		type.asmType = Type.getType("[" + elementType.getRawType().getDescriptor());
		type.nullableDimensions = nullableDimensions;
		return type;
	}

	public static WaterType getObjectType(String internalName) {
		return new WaterType(Type.getObjectType(internalName));
	}

	public static WaterType getType(String descriptor) {
		return new WaterType(Type.getType(descriptor));
	}

	public static WaterType getType(Class<?> clazz) {
		return new WaterType(Type.getType(clazz));
	}

	private static WaterType getArrayFromAnnotation(Class<?> typeClass, Nullable annotation) {
		int dim = 1;
		Class<?> elementClass = typeClass.getComponentType();

		while(elementClass.isArray()) {
			dim++;
			elementClass = elementClass.getComponentType();
		}
		WaterType elementType = WaterType.getType(elementClass);

		boolean isNullable = false;
		List<Integer> nullableDimensions = null;
		if(annotation != null) {
			nullableDimensions = Arrays.stream(annotation.d()).boxed().collect(Collectors.toList());
			if(nullableDimensions.contains(-1)) {
				nullableDimensions.remove(Integer.valueOf(-1));
				isNullable = true;
			}
			if(nullableDimensions.contains(-2)) {
				nullableDimensions.remove(Integer.valueOf(-2));
				elementType.isNullable = true;
			}
		}
		return WaterType.getArrayType(elementType, dim, nullableDimensions).asNullable(isNullable);
	}

	public static WaterType getType(Field field) {
		WaterType type = WaterType.getType(field.getType());

		Class<?> fieldClass = field.getType();
		if(fieldClass.isArray()) {
			type = getArrayFromAnnotation(fieldClass, field.getAnnotation(Nullable.class));
		}
		else if(field.getAnnotation(Nullable.class) != null) {
			type.isNullable = true;
		}
		return type;
	}

	public static WaterType getType(Method method) {
		Parameter[] parameters = method.getParameters();

		ArrayList<WaterType> parameterTypes = new ArrayList<>();
		for(Parameter parameter : parameters) {
			Class<?> typeClass = parameter.getType();

			WaterType type = WaterType.getType(typeClass);

			if(typeClass.isArray()) {
				type = getArrayFromAnnotation(typeClass, parameter.getAnnotation(Nullable.class));
			}
			else if(parameter.getAnnotation(Nullable.class) != null) {
				type.isNullable = true;
			}
			parameterTypes.add(type);
		}
		WaterType returnType = WaterType.getType(method.getReturnType());

		Class<?> returnClass = method.getReturnType();

		if(returnClass.isArray()) {
			returnType = getArrayFromAnnotation(returnClass, method.getAnnotation(Nullable.class));
		}
		else if(method.getAnnotation(Nullable.class) != null) {
			returnType.isNullable = true;
		}
		return WaterType.getMethodType(returnType, parameterTypes.toArray(WaterType[]::new));
	}

	public static WaterType getType(Constructor<?> constructor) {
		Parameter[] parameters = constructor.getParameters();

		ArrayList<WaterType> parameterTypes = new ArrayList<>();
		for(Parameter parameter : parameters) {
			Class<?> typeClass = parameter.getType();

			WaterType type = WaterType.getType(typeClass);
			if(typeClass.isArray()) {
				type = getArrayFromAnnotation(typeClass, parameter.getAnnotation(Nullable.class));
			}
			else if(parameter.getAnnotation(Nullable.class) != null) {
				type.isNullable = true;
			}
			parameterTypes.add(type);
		}

		return WaterType.getMethodType(WaterType.VOID_TYPE, parameterTypes.toArray(WaterType[]::new));
	}
}
