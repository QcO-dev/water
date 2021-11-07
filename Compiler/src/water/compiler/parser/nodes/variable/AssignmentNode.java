package water.compiler.parser.nodes.variable;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.compiler.Variable;
import water.compiler.compiler.VariableType;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.nullability.NullableMemberAccessNode;
import water.compiler.parser.nodes.operation.ArithmeticOperationNode;
import water.compiler.parser.nodes.operation.IntegerOperationNode;
import water.compiler.parser.nodes.value.ThisNode;
import water.compiler.util.Pair;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AssignmentNode implements Node {

	private final Token op;
	private final Node left;
	private final Node right;
	// For property assignment static checking
	private boolean isStaticAccess;

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

		WaterType returnType = right.getReturnType(context.getContext());

		context.getContext().updateLine(op.getLine());

		switch (valueType) {
			case VARIABLE -> variable(context, returnType);
			case PROPERTY -> property(context, returnType);
			case ARRAY -> array(context, returnType);
			case NULLABLE_PROPERTY -> nullableProperty(context, returnType);
			case NULLABLE_ARRAY -> nullableArray(context, returnType);
		}
	}

	private void variable(FileContext context, WaterType returnType) throws SemanticException {
		Object[] lValueData = left.getLValueData();
		Token name = (Token) lValueData[0]; // From a VariableAccessNode, the first item is the token of its name.

		Variable variable = context.getContext().getScope().lookupVariable(name.getValue());

		if(variable == null) {
			throw new SemanticException(op, "Cannot resolve variable '%s' in current scope.".formatted(name.getValue()));
		}

		if(variable.isConst()) {
			throw new SemanticException(name, "Reassignment of constant '%s'.".formatted(name.getValue()));
		}

		if(variable.getVariableType() == VariableType.CLASS) {
			if(context.getContext().isStaticMethod())  throw new SemanticException(name, "Cannot access instance member '%s' in a static context".formatted(name.getValue()));
			context.getContext().getMethodVisitor().visitVarInsn(Opcodes.ALOAD, 0);
		}

		generateSyntheticOperation().visit(context);

		try {
			if(!variable.getType().isAssignableFrom(returnType, context.getContext(), true)) {
				throw new SemanticException(op,
						"Cannot assign type '%s' to variable of type '%s'"
								.formatted(returnType, variable.getType()));
			}
		} catch (ClassNotFoundException e) {
			throw new SemanticException(op, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();
		if(!isExpressionStatementBody) methodVisitor.visitInsn(returnType.getDupOpcode());

		if(variable.getVariableType() == VariableType.STATIC) {
			methodVisitor.visitFieldInsn(Opcodes.PUTSTATIC, variable.getOwner(), variable.getName(), variable.getType().getDescriptor());
		}
		if(variable.getVariableType() == VariableType.CLASS) {
			methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, variable.getOwner(), variable.getName(), variable.getType().getDescriptor());
		}
		else {
			methodVisitor.visitVarInsn(variable.getType().getOpcode(Opcodes.ISTORE), variable.getIndex());
		}
	}

	private void property(FileContext context, WaterType returnType) throws SemanticException {
		Object[] lValueData = left.getLValueData();

		Node obj = (Node) lValueData[0];
		Token name = (Token) lValueData[1];

		obj.visit(context);

		generateSyntheticOperation().visit(context);

		WaterType objType = getObjectType(obj, context.getContext());

		if(!objType.isObject()) {
			throw new SemanticException(name, "Cannot access member on type '%s'".formatted(objType));
		}

		if(objType.isNullable()) {
			throw new SemanticException(name, "Cannot use '.' to access members on a nullable type ('%s')".formatted(objType));
		}

		handlePropertySettingLogic(obj, objType, name, returnType, context);
	}

	private void array(FileContext context, WaterType returnType) throws SemanticException {
		Object[] lValueData = left.getLValueData();

		Node array = (Node) lValueData[0];
		Node index = (Node) lValueData[1];
		Token bracket = (Token) lValueData[2];

		WaterType arrayType = array.getReturnType(context.getContext());
		WaterType indexType = index.getReturnType(context.getContext());

		if(!arrayType.isArray()) {
			throw new SemanticException(op, "Cannot get index of type '%s'".formatted(arrayType));
		}
		if(arrayType.isNullable()) {
			throw new SemanticException(bracket, "Cannot use '[' to access members on a nullable type ('%s')".formatted(arrayType));
		}

		if(!indexType.isInteger()) {
			throw new SemanticException(op, "Index must be an integer type (got '%s')".formatted(indexType));
		}

		array.visit(context);
		index.visit(context);

		try {
			if(!arrayType.getElementType().isAssignableFrom(returnType, context.getContext(), true)) {
				throw new SemanticException(op,
						"Cannot assign type '%s' to element of type '%s'"
								.formatted(returnType, arrayType.getElementType()));
			}
		} catch (ClassNotFoundException e) {
			throw new SemanticException(op, "Could not resolve class '%s'".formatted(e.getMessage()));
		}
		generateSyntheticOperation().visit(context);

		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();

		if(!isExpressionStatementBody) methodVisitor.visitInsn(returnType.getDupX2Opcode());

		methodVisitor.visitInsn(arrayType.getElementType().getOpcode(Opcodes.IASTORE));
	}

	private void nullableProperty(FileContext context, WaterType returnType) throws SemanticException {
		Object[] lValueData = left.getLValueData();

		Node obj = (Node) lValueData[0];
		Token name = (Token) lValueData[1];

		WaterType objType = getObjectType(obj, context.getContext());

		if(!objType.isObject()) {
			throw new SemanticException(name, "Cannot access member on type '%s'".formatted(objType));
		}

		if(!objType.isNullable()) {
			throw new SemanticException(name, "Cannot use '?.' to access members on a non-nullable type ('%s')".formatted(objType));
		}

		Label nullJump = new Label();
		Label end = new Label();

		context.getContext().setNullJumpLabel(nullJump);

		obj.visit(context);

		context.getContext().setNullJumpLabel(null);

		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();

		methodVisitor.visitInsn(Opcodes.DUP);
		methodVisitor.visitJumpInsn(Opcodes.IFNULL, nullJump);

		generateSyntheticOperation().visit(context);

		handlePropertySettingLogic(obj, objType, name, returnType, context);

		methodVisitor.visitJumpInsn(Opcodes.GOTO, end);

		methodVisitor.visitLabel(nullJump);
		methodVisitor.visitInsn(Opcodes.POP);
		methodVisitor.visitLabel(end);
	}

	private void nullableArray(FileContext context, WaterType returnType) throws SemanticException {
		Object[] lValueData = left.getLValueData();

		Node array = (Node) lValueData[0];
		Node index = (Node) lValueData[1];
		Token bracket = (Token) lValueData[2];

		WaterType arrayType = array.getReturnType(context.getContext());
		WaterType indexType = index.getReturnType(context.getContext());

		if(!arrayType.isArray()) {
			throw new SemanticException(op, "Cannot get index of type '%s'".formatted(arrayType));
		}
		if(!arrayType.isNullable()) {
			throw new SemanticException(bracket, "Cannot use '?[' to access members on a non-nullable type ('%s')".formatted(arrayType));
		}

		if(!indexType.isInteger()) {
			throw new SemanticException(op, "Index must be an integer type (got '%s')".formatted(indexType));
		}

		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();
		Label nullJump = new Label();
		Label end = new Label();

		context.getContext().setNullJumpLabel(nullJump);

		array.visit(context);

		context.getContext().setNullJumpLabel(null);

		methodVisitor.visitInsn(arrayType.getDupOpcode());
		methodVisitor.visitJumpInsn(Opcodes.IFNULL, nullJump);

		index.visit(context);

		try {
			if(!arrayType.getElementType().isAssignableFrom(returnType, context.getContext(), true)) {
				throw new SemanticException(op,
						"Cannot assign type '%s' to element of type '%s'"
								.formatted(returnType, arrayType.getElementType()));
			}
		} catch (ClassNotFoundException e) {
			throw new SemanticException(op, "Could not resolve class '%s'".formatted(e.getMessage()));
		}
		generateSyntheticOperation().visit(context);


		if(!isExpressionStatementBody) methodVisitor.visitInsn(returnType.getDupX2Opcode());

		methodVisitor.visitInsn(arrayType.getElementType().getOpcode(Opcodes.IASTORE));

		methodVisitor.visitJumpInsn(Opcodes.GOTO, end);

		methodVisitor.visitLabel(nullJump);
		methodVisitor.visitInsn(Opcodes.POP);
		methodVisitor.visitLabel(end);

	}

	private void handlePropertySettingLogic(Node obj, WaterType objType, Token name, WaterType returnType, FileContext context) throws SemanticException {
		Class<?> klass;

		try {
			klass = Class.forName(objType.getClassName(), false, context.getContext().getLoader());
		} catch (ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		if(!isExpressionStatementBody) context.getContext().getMethodVisitor().visitInsn(returnType.getDupX1Opcode());

		try {
			Field f = klass.getDeclaredField(name.getValue());

			try {
				if(!WaterType.getType(f).isAssignableFrom(returnType, context.getContext(), true)) {
					throw new SemanticException(op,
							"Cannot assign type '%s' to variable of type '%s'"
									.formatted(returnType, WaterType.getType(f.getType())));
				}
			} catch (ClassNotFoundException e) {
				throw new SemanticException(op, "Could not resolve class '%s'".formatted(e.getMessage()));
			}

			//TODO Protected
			if(!Modifier.isPublic(f.getModifiers()) && !objType.equals(WaterType.getObjectType(context.getContext().getCurrentClass()))) {
				throw new NoSuchFieldException();
			}

			if(!isStaticAccess && Modifier.isStatic(f.getModifiers())) {
				throw new SemanticException(name, "Cannot access static member from non-static object.");
			}

			if(Modifier.isFinal(f.getModifiers())) {
				if (!(obj instanceof ThisNode) || !context.getContext().isConstructor())
					throw new SemanticException(name, "Cannot assign final member '%s'".formatted(name.getValue()));
			}

			context.getContext().getMethodVisitor().visitFieldInsn(TypeUtil.getMemberPutOpcode(f),
					objType.getInternalName(), name.getValue(), Type.getType(f.getType()).getDescriptor());

		} catch (NoSuchFieldException e) {
			String base = name.getValue().substring(0, 1).toUpperCase() + name.getValue().substring(1);
			String setName = "set" + (name.getValue().matches("^is[\\p{Lu}].*") ? base.substring(2) : base);

			Method m = resolveSetMethod(klass, name, setName, returnType, context.getContext());

			String descriptor = "(%s)V".formatted(WaterType.getType(m.getParameterTypes()[0]).getDescriptor());

			context.getContext().getMethodVisitor().visitMethodInsn(TypeUtil.getInvokeOpcode(m),
					objType.getInternalName(), setName, descriptor, false);
		}
	}

	private Method resolveSetMethod(Class<?> klass, Token location, String name, WaterType arg, Context context) throws SemanticException {
		ArrayList<Pair<Integer, Method>> possible = new ArrayList<>();

		try {
			for (Method method : klass.getMethods()) {
				if(!method.getName().equals(name)) continue;
				WaterType[] expectArgs = WaterType.getType(method).getArgumentTypes();

				if (expectArgs.length != 1) continue;

				int changes = 0;

				WaterType expectArg = expectArgs[0];

				if (arg.equals(WaterType.VOID_TYPE))
					continue;

				if (expectArg.isAssignableFrom(arg, context, false)) {
					if (!expectArg.equals(arg)) changes += expectArg.assignChangesFrom(arg);
				} else {
					continue;
				}
				possible.add(new Pair<>(changes, method));
			}
		}
		catch(ClassNotFoundException e) {
			throw new SemanticException(location, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		if(possible.size() == 0) {
			throw new SemanticException(location,
					"Could not resolve field '%s' in class '%s' with type '%s'".formatted(name, klass.getName(),
							arg));
		}

		List<Pair<Integer, Method>> appliedPossible = possible.stream()
				.filter(p -> Modifier.isStatic(p.getSecond().getModifiers()) == isStaticAccess)
				.sorted(Comparator.comparingInt(Pair::getFirst)).toList();

		if(appliedPossible.size() == 0) {
			if(isStaticAccess)
				// Shouldn't be thrown
				throw new SemanticException(location, "Cannot invoke non-static member from static class.");
			else
				throw new SemanticException(location, "Cannot access static member from non-static object.");
		}

		return appliedPossible.get(0).getSecond();
	}

	private Token makeSyntheticToken() {
		return new Token(switch(op.getType()) {
			case IN_PLUS -> TokenType.PLUS;
			case IN_MINUS -> TokenType.MINUS;
			case IN_MUL -> TokenType.STAR;
			case IN_DIV -> TokenType.SLASH;
			case IN_MOD -> TokenType.PERCENT;
			case IN_BITWISE_AND -> TokenType.BITWISE_AND;
			case IN_BITWISE_OR -> TokenType.BITWISE_OR;
			case IN_BITWISE_XOR -> TokenType.BITWISE_XOR;
			case IN_BITWISE_SHL -> TokenType.BITWISE_SHL;
			case IN_BITWISE_SHR -> TokenType.BITWISE_SHR;
			case IN_BITWISE_USHR -> TokenType.BITWISE_USHR;
			default -> null;
		}, op.getValue(), op.getLine(), op.getColumn());
	}

	private boolean isBitwise(TokenType type) {
		return switch (type) {
			case IN_BITWISE_AND, IN_BITWISE_OR, IN_BITWISE_XOR, IN_BITWISE_SHL, IN_BITWISE_SHR, IN_BITWISE_USHR -> true;
			default -> false;
		};
	}

	private Node generateSyntheticOperation() {
		if(op.getType() == TokenType.EQUALS) return right;

		Token syntheticOp = makeSyntheticToken();

		if(isBitwise(op.getType())) {
			return new IntegerOperationNode(left, syntheticOp, right);
		}

		return new ArithmeticOperationNode(left, syntheticOp, right);
	}

	private WaterType getObjectType(Node obj, Context context) throws SemanticException {
		if(obj instanceof VariableAccessNode) {
			VariableAccessNode van = (VariableAccessNode) obj;
			van.setMemberAccess(true);
			WaterType leftType = obj.getReturnType(context);
			isStaticAccess = van.isStaticClassAccess();
			return leftType;
		}
		return obj.getReturnType(context);
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
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
