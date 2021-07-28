package water.compiler.parser.nodes.classes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MemberAccessNode implements Node {

	private final Node left;
	private final Token name;

	public MemberAccessNode(Node left, Token name) {
		this.left = left;
		this.name = name;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Type leftType = left.getReturnType(context.getContext());

		if(leftType.getSort() == Type.ARRAY && name.getValue().equals("length")) {
			left.visit(context);
			context.getContext().getMethodVisitor().visitInsn(Opcodes.ARRAYLENGTH);
			return;
		}

		if(leftType.getSort() != Type.OBJECT) {
			throw new SemanticException(name, "Cannot access member on type '%s'".formatted(TypeUtil.stringify(leftType)));
		}

		left.visit(context);

		resolve(leftType, context.getContext(), true);
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		Type leftType = left.getReturnType(context);

		if(leftType.getSort() == Type.ARRAY && name.getValue().equals("length")) return Type.INT_TYPE;

		return resolve(leftType, context, false);
	}

	private Type resolve(Type leftType, Context context, boolean generate) throws SemanticException {
		Class<?> klass;

		try {
			klass = Class.forName(leftType.getClassName(), false, context.getLoader());
		} catch (ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		try {
			Field f = klass.getField(name.getValue());

			if(generate) {
				context.getMethodVisitor().visitFieldInsn(TypeUtil.getAccessOpcode(f),
						leftType.getInternalName(), name.getValue(), Type.getType(f.getType()).getDescriptor());
			}

			return Type.getType(f.getType());
		} catch (NoSuchFieldException e) {
			String base = name.getValue();
			String getName = "get" + base.substring(0, 1).toUpperCase() + base.substring(1);

			// This code is disgusting, but I cannot see a better way.
			try {
				return attemptMethodCall(klass, leftType, getName, generate, context);
			} catch (NoSuchMethodException noSuchMethodException) {
				try {
					return attemptMethodCall(klass, leftType, base, generate, context);
				} catch (NoSuchMethodException suchMethodException) {
					throw new SemanticException(name, "Could not resolve field '%s' in class '%s'".formatted(name.getValue(), TypeUtil.stringify(leftType)));
				}
			}
		}
	}

	private Type attemptMethodCall(Class<?> klass, Type leftType, String methodName, boolean generate, Context context) throws NoSuchMethodException {
		Method m = klass.getMethod(methodName);

		Type returnType = Type.getType(m.getReturnType());

		if(generate) {
			context.getMethodVisitor().visitMethodInsn(TypeUtil.getInvokeOpcode(m),
					leftType.getInternalName(), methodName, "()" + returnType.getDescriptor(), false);
		}

		return returnType;
	}

	@Override
	public LValue getLValue() {
		return LValue.PROPERTY;
	}

	@Override
	public Object[] getLValueData() {
		return new Object[] { left, name };
	}

	@Override
	public String toString() {
		return "%s.%s".formatted(left, name.getValue());
	}
}
