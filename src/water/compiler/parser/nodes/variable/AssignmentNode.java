package water.compiler.parser.nodes.variable;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.compiler.Variable;
import water.compiler.compiler.VariableType;
import water.compiler.lexer.Token;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AssignmentNode implements Node {

	private final Token op;
	private final Node left;
	private final Node right;

	private boolean isExpressionStatementBody = false;

	public AssignmentNode(Node left, Token op, Node right) {
		this.op = op;
		this.left = left;
		this.right = right;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		LValue valueType = left.getLValue();

		if(valueType == LValue.NONE) {
			throw new SemanticException(op, "Invalid lvalue - cannot assign");
		}

		Type returnType = right.getReturnType(context.getContext());

		context.getContext().updateLine(op.getLine());

		if(valueType == LValue.VARIABLE) {
			variable(context, returnType);
		}
		else if(valueType == LValue.PROPERTY) {
			property(context, returnType);
		}
		else if(valueType == LValue.ARRAY) {
			array(context, returnType);
		}
	}

	private void variable(FileContext context, Type returnType) throws SemanticException {
		Object[] lValueData = left.getLValueData();
		Token name = (Token) lValueData[0]; // From a VariableAccessNode, the first item is the token of its name.

		Variable variable = context.getContext().getScope().lookupVariable(name.getValue());

		if(variable == null) {
			throw new SemanticException(op, "Cannot resolve variable '%s' in current scope.".formatted(name.getValue()));
		}

		try {
			if(!TypeUtil.isAssignableFrom(variable.getType(), returnType, context.getContext(), true)) {
				throw new SemanticException(op,
						"Cannot assign type '%s' to variable of type '%s'"
								.formatted(TypeUtil.stringify(returnType), TypeUtil.stringify(variable.getType())));
			}
		} catch (ClassNotFoundException e) {
			throw new SemanticException(op, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		right.visit(context);

		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();
		if(!isExpressionStatementBody) methodVisitor.visitInsn(TypeUtil.getDupOpcode(returnType));

		if(variable.getVariableType() == VariableType.GLOBAL) {
			methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, variable.getOwner(), variable.getName(), variable.getType().getDescriptor());
		}
		else {
			methodVisitor.visitVarInsn(variable.getType().getOpcode(Opcodes.ISTORE), variable.getIndex());
		}
	}

	private void property(FileContext context, Type returnType) throws SemanticException {
		Object[] lValueData = left.getLValueData();

		Node obj = (Node) lValueData[0];
		Token name = (Token) lValueData[1];

		obj.visit(context);

		right.visit(context);

		Type objType = obj.getReturnType(context.getContext());

		if(objType.getSort() != Type.OBJECT) {
			throw new SemanticException(name, "Cannot access member on type '%s'".formatted(TypeUtil.stringify(objType)));
		}

		Class<?> klass;

		try {
			klass = Class.forName(objType.getClassName(), false, context.getContext().getLoader());
		} catch (ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		if(!isExpressionStatementBody) context.getContext().getMethodVisitor().visitInsn(TypeUtil.getDupX1Opcode(returnType));

		try {
			Field f = klass.getField(name.getValue());

			try {
				if(!TypeUtil.isAssignableFrom(Type.getType(f.getType()), returnType, context.getContext(), true)) {
					throw new SemanticException(op,
							"Cannot assign type '%s' to variable of type '%s'"
									.formatted(TypeUtil.stringify(returnType), TypeUtil.stringify(Type.getType(f.getType()))));
				}
			} catch (ClassNotFoundException e) {
				throw new SemanticException(op, "Could not resolve class '%s'".formatted(e.getMessage()));
			}

			context.getContext().getMethodVisitor().visitFieldInsn(TypeUtil.getMemberPutOpcode(f),
					objType.getInternalName(), name.getValue(), Type.getType(f.getType()).getDescriptor());

		} catch (NoSuchFieldException e) {
			String base = name.getValue().substring(0, 1).toUpperCase() + name.getValue().substring(1);
			String setName = "set" + (name.getValue().matches("^is[\\p{Lu}].*") ? base.substring(2) : base);

			try {
				Method m = klass.getMethod(setName, TypeUtil.typeToClass(returnType, context.getContext()));

				String descriptor = "(%s)V".formatted(Type.getType(m.getParameterTypes()[0]).getDescriptor());

				context.getContext().getMethodVisitor().visitMethodInsn(TypeUtil.getInvokeOpcode(m),
						objType.getInternalName(), setName, descriptor, false);

			} catch (NoSuchMethodException noSuchMethodException) {
				throw new SemanticException(name, "Could not resolve field '%s' in class '%s' with type '%s'"
						.formatted(name.getValue(), TypeUtil.stringify(objType), TypeUtil.stringify(returnType)));
			} catch (ClassNotFoundException classNotFoundException) {
				throw new SemanticException(op, "Could not resolve class '%s'".formatted(e.getMessage()));
			}

		}

	}

	private void array(FileContext context, Type returnType) throws SemanticException {
		Object[] lValueData = left.getLValueData();

		Node array = (Node) lValueData[0];
		Node index = (Node) lValueData[1];

		Type arrayType = array.getReturnType(context.getContext());
		Type indexType = index.getReturnType(context.getContext());

		if(arrayType.getSort() != Type.ARRAY) {
			throw new SemanticException(op, "Cannot get index of type '%s'".formatted(TypeUtil.stringify(arrayType)));
		}
		if(!TypeUtil.isInteger(indexType)) {
			throw new SemanticException(op, "Index must be an integer type (got '%s')".formatted(TypeUtil.stringify(indexType)));
		}

		array.visit(context);
		index.visit(context);

		try {
			if(!TypeUtil.isAssignableFrom(arrayType.getElementType(), returnType, context.getContext(), true)) {
				throw new SemanticException(op,
						"Cannot assign type '%s' to element of type '%s'"
								.formatted(TypeUtil.stringify(returnType), TypeUtil.stringify(arrayType.getElementType())));
			}
		} catch (ClassNotFoundException e) {
			throw new SemanticException(op, "Could not resolve class '%s'".formatted(e.getMessage()));
		}
		right.visit(context);

		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();

		if(!isExpressionStatementBody) methodVisitor.visitInsn(TypeUtil.getDupX2Opcode(returnType));

		methodVisitor.visitInsn(arrayType.getElementType().getOpcode(Opcodes.IASTORE));
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return right.getReturnType(context);
	}

	@Override
	public String toString() {
		return "%s %s %s".formatted(left, op.getValue(), right);
	}

	public void setExpressionStatementBody(boolean expressionStatementBody) {
		isExpressionStatementBody = expressionStatementBody;
	}
}
