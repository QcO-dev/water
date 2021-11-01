package water.compiler.util;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Extra utility for dealing with ASM Type objects.
 *
 * @see org.objectweb.asm.Type
 */
public class TypeUtil {
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
	 * Creates the optimal opcode for the given float.
	 * @param val The float to generate bytecode for.
	 * @param context The context of the method.
	 */
	public static void generateCorrectFloat(float val, Context context) {
		MethodVisitor method = context.getMethodVisitor();

		if (val == 0) method.visitInsn(Opcodes.FCONST_0);
		else if (val == 1) method.visitInsn(Opcodes.FCONST_1);
		else if (val == 2) method.visitInsn(Opcodes.FCONST_2);
		else method.visitLdcInsn(val);
	}

	/**
	 * Creates the optimal opcode for the given long.
	 * @param val The float to generate bytecode for.
	 * @param context The context of the method.
	 */
	public static void generateCorrectLong(long val, Context context) {
		MethodVisitor method = context.getMethodVisitor();

		if (val == 0) method.visitInsn(Opcodes.LCONST_0);
		else if (val == 1) method.visitInsn(Opcodes.LCONST_1);
		else method.visitLdcInsn(val);
	}

	/**
	 * Given an object, creates the optimal opcode to load it in bytecode (where possible).
	 * @param object The object to add to bytecode.
	 * @param context The context of the method.
	 */
	public static void correctLdc(Object object, Context context) {
		if(object == null) {
			context.getMethodVisitor().visitInsn(Opcodes.ACONST_NULL);
		}
		else if(object instanceof Integer) {
			generateCorrectInt((Integer) object, context);
		}
		else if(object instanceof Double) {
			generateCorrectDouble((Double) object, context);
		}
		else if(object instanceof Float) {
			generateCorrectFloat((Float) object, context);
		}
		else if(object instanceof Boolean) {
			context.getMethodVisitor().visitInsn((Boolean) object ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
		}
		else if(object instanceof Character) {
			generateCorrectInt((Character) object, context);
		}
		else if(object instanceof Long) {
			generateCorrectLong((Long) object, context);
		}
		else if(object instanceof Byte) {
			generateCorrectInt((Byte) object, context);
		}
		else if(object instanceof Short) {
			generateCorrectInt((Short) object, context);
		}
		else {
			context.getMethodVisitor().visitLdcInsn(object);
		}
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

	/**
	 * Looks up a class for the given name, using imports to resolve.
	 * @param name The name of the class
	 * @param context The context to use for the {@link water.compiler.WaterClassLoader} and imports
	 * @return The resolved Class
	 * @throws ClassNotFoundException If the class cannot be resolved
	 */
	public static Class<?> classForName(String name, Context context) throws ClassNotFoundException {
		String className = name;

		if(context.getImports().get(name) != null) className = context.getImports().get(name);

		return Class.forName(className, false, context.getLoader());
	}

	/**
	 *
	 * Resolves the most suitable constructor, given a list of types.
	 *
	 * @param location The location of the token which leads to the calling of this constructor.
	 * @param constructors All the possible constructors.
	 * @param argTypes The argument types.
	 * @param context The current context
	 * @return The most suitable constructor
	 * @throws SemanticException If a class cannot be resolved
	 */
	public static Constructor<?> getConstructor(Token location, Constructor<?>[] constructors, WaterType[] argTypes, Context context) throws SemanticException {

		ArrayList<Pair<Integer, Constructor<?>>> possible = new ArrayList<>();

		try {
			out:
			for (Constructor<?> c : constructors) {
				WaterType[] expectArgs = new WaterType(Type.getType(c)).getArgumentTypes();

				if (expectArgs.length != argTypes.length) continue;

				int changes = 0;

				for (int i = 0; i < expectArgs.length; i++) {
					WaterType expectArg = expectArgs[i];
					WaterType arg = argTypes[i];

					if (arg.equals(WaterType.VOID_TYPE))
						continue out;

					if (expectArg.isAssignableFrom(arg, context, false)) {
						if (!expectArg.equals(arg)) changes += expectArg.assignChangesFrom(arg);
					} else {
						continue out;
					}
				}
				possible.add(new Pair<>(changes, c));
			}
		}
		catch(ClassNotFoundException e) {
			throw new SemanticException(location, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		if(possible.size() == 0) return null;

		possible.sort(Comparator.comparingInt(Pair::getFirst));

		return possible.get(0).getSecond();
	}

}
