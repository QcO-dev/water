package water.compiler.parser.nodes.classes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.variable.VariableAccessNode;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MemberAccessNode implements Node {

	private final Node left;
	private final Token name;
	private boolean isStaticAccess;

	public MemberAccessNode(Node left, Token name) {
		this.left = left;
		this.name = name;
		this.isStaticAccess = false;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		getLeftType(context.getContext()); // Initializes the variable access node to a static state (if in a static access)
		left.visit(context);

		WaterType returnType = left.getReturnType(context.getContext());
		if(returnType.isNullable()) {
			throw new SemanticException(name, "Cannot use '.' to access members on a nullable type ('%s')".formatted(returnType));
		}

		visitAccess(context);
	}

	public void visitAccess(FileContext context) throws SemanticException {
		WaterType leftType = getLeftType(context.getContext());

		if(leftType.isArray() && name.getValue().equals("length")) {
			context.getContext().getMethodVisitor().visitInsn(Opcodes.ARRAYLENGTH);
			return;
		}

		if(!leftType.isObject()) {
			throw new SemanticException(name, "Cannot access member on type '%s'".formatted(leftType));
		}

		resolve(leftType, context.getContext(), true);
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		WaterType leftType = getLeftType(context);

		if(leftType.isArray() && name.getValue().equals("length")) return WaterType.INT_TYPE;

		return resolve(leftType, context, false);
	}

	private WaterType getLeftType(Context context) throws SemanticException {
		if(left instanceof VariableAccessNode) {
			VariableAccessNode van = (VariableAccessNode) left;
			van.setMemberAccess(true);
			WaterType leftType = left.getReturnType(context);
			isStaticAccess = van.isStaticClassAccess();
			return leftType;
		}
		return left.getReturnType(context);
	}

	private WaterType resolve(WaterType leftType, Context context, boolean generate) throws SemanticException {
		Class<?> klass;

		try {
			klass = Class.forName(leftType.getClassName(), false, context.getLoader());
		} catch (ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		try {
			Field f = klass.getDeclaredField(name.getValue());

			//TODO Protected
			if(!Modifier.isPublic(f.getModifiers()) && !leftType.equals(WaterType.getObjectType(context.getCurrentClass()))) {
				throw new NoSuchFieldException();
			}

			if(!isStaticAccess && Modifier.isStatic(f.getModifiers())) {
				throw new SemanticException(name, "Cannot access static member from non-static object.");
			}

			if(generate) {
				context.getMethodVisitor().visitFieldInsn(TypeUtil.getAccessOpcode(f),
						leftType.getInternalName(), name.getValue(), Type.getType(f.getType()).getDescriptor());
			}

			return WaterType.getType(f.getType());
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
					throw new SemanticException(name, "Could not resolve field '%s' in class '%s'".formatted(name.getValue(), leftType));
				}
			}
		}
	}

	private WaterType attemptMethodCall(Class<?> klass, WaterType leftType, String methodName, boolean generate, Context context) throws NoSuchMethodException, SemanticException {
		Method m = klass.getMethod(methodName);

		WaterType returnType = WaterType.getType(m.getReturnType());

		if(!isStaticAccess && Modifier.isStatic(m.getModifiers())) {
			throw new SemanticException(name, "Cannot access static member from non-static object.");
		}

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
