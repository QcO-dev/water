package water.compiler.parser.nodes.operation;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.compiler.Variable;
import water.compiler.compiler.VariableType;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.classes.MemberAccessNode;
import water.compiler.parser.nodes.variable.AssignmentNode;
import water.compiler.parser.nodes.variable.VariableAccessNode;
import water.compiler.util.WaterType;

public class UpdateExpressionNode implements Node {

	private final Node expression;
	private final Token operation;
	private final boolean prefix;
	private final boolean increment;
	private boolean isStaticAccess;

	public UpdateExpressionNode(Node expression, Token operation, boolean prefix) {
		this.expression = expression;
		this.operation = operation;
		this.prefix = prefix;
		this.increment = operation.getType() == TokenType.INCREMENT;
		this.isStaticAccess = false;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		if(expression.getLValue() == LValue.NONE) {
			throw new SemanticException(operation, "Invalid lvalue - cannot perform operation");
		}

		switch (expression.getLValue()) {
			case VARIABLE -> variable(context);
			case ARRAY -> array(context);
			case PROPERTY -> property(context);
			default -> throw new SemanticException(operation, "Invalid lvalue - cannot perform operation");
		}

	}

	private void variable(FileContext context) throws SemanticException {
		Object[] lValueData = expression.getLValueData();
		Token name = (Token) lValueData[0]; // From a VariableAccessNode, the first item is the token of its name.

		Variable variable = context.getContext().getScope().lookupVariable(name.getValue());

		if(variable == null) {
			throw new SemanticException(operation, "Cannot resolve variable '%s' in current scope.".formatted(name.getValue()));
		}

		if(variable.isConst()) {
			throw new SemanticException(operation, "Reassignment of constant '%s'.".formatted(name.getValue()));
		}

		if(variable.getVariableType() == VariableType.CLASS) {
			if (context.getContext().isStaticMethod())
				throw new SemanticException(name, "Cannot access instance member '%s' in a static context".formatted(name.getValue()));
		}

		if(!variable.getType().isNumeric()) {
			throw new SemanticException(operation, "Update expression ('%s') target must be numeric (got '%s')".formatted(operation.getValue(),
					variable.getType()));
		}

		WaterType type = variable.getType();

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		// Optimise local variables and integers with iinc instruction
		if(variable.getVariableType() == VariableType.LOCAL && type.getSort() == WaterType.Sort.INT) {
			int value = increment ? 1 : -1;

			if(!prefix) visitor.visitVarInsn(Opcodes.ILOAD, variable.getIndex());

			visitor.visitIincInsn(variable.getIndex(), value);

			if(prefix) visitor.visitVarInsn(Opcodes.ILOAD, variable.getIndex());

			return;
		}

		switch (variable.getVariableType()) {
			case LOCAL ->  {
				visitor.visitVarInsn(type.getOpcode(Opcodes.ILOAD), variable.getIndex());
				if(!prefix) visitor.visitInsn(type.getDupOpcode());
			}
			case STATIC ->  {
				visitor.visitFieldInsn(Opcodes.GETSTATIC, variable.getOwner(), variable.getName(), variable.getType().getDescriptor());
				if(!prefix) visitor.visitInsn(type.getDupOpcode());
			}
			case CLASS -> {
				visitor.visitVarInsn(Opcodes.ALOAD, 0);
				visitor.visitInsn(Opcodes.DUP);
				visitor.visitFieldInsn(Opcodes.GETFIELD, variable.getOwner(), variable.getName(), variable.getType().getDescriptor());
				if(!prefix) visitor.visitInsn(type.getDupX1Opcode());
			}
		}

		// Either add or subtract 1 of the specified type
		type.generateAsInteger(1, context.getContext());

		if(increment) visitor.visitInsn(type.getOpcode(Opcodes.IADD));
		else visitor.visitInsn(type.getOpcode(Opcodes.ISUB));

		switch (variable.getVariableType()) {
			case LOCAL -> {
				if(prefix) visitor.visitInsn(type.getDupOpcode());
				visitor.visitVarInsn(type.getOpcode(Opcodes.ISTORE), variable.getIndex());
			}
			case STATIC ->  {
				if(prefix) visitor.visitInsn(type.getDupOpcode());
				visitor.visitFieldInsn(Opcodes.PUTSTATIC, variable.getOwner(), variable.getName(), variable.getType().getDescriptor());
			}
			case CLASS -> {
				if(prefix) visitor.visitInsn(type.getDupX1Opcode());
				visitor.visitFieldInsn(Opcodes.PUTFIELD, variable.getOwner(), variable.getName(), variable.getType().getDescriptor());
			}
		}

	}

	private void array(FileContext context) throws SemanticException {
		Object[] lValueData = expression.getLValueData();

		Node array = (Node) lValueData[0];
		Node index = (Node) lValueData[1];
		Token bracket = (Token) lValueData[2];

		WaterType arrayType = array.getReturnType(context.getContext());
		WaterType indexType = index.getReturnType(context.getContext());

		if(!arrayType.isArray()) {
			throw new SemanticException(bracket, "Cannot get index of type '%s'".formatted(arrayType));
		}
		if(arrayType.isNullable()) {
			throw new SemanticException(bracket, "Cannot use '[' to access members on a nullable type ('%s')".formatted(arrayType));
		}

		if(!indexType.isRepresentedAsInteger()) {
			throw new SemanticException(bracket, "Index must be an integer type (got '%s')".formatted(indexType));
		}

		WaterType elementType = arrayType.getElementType();

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		if(!elementType.isNumeric()) {
			throw new SemanticException(operation, "Update expression ('%s') target must be numeric (got '%s')".formatted(operation.getValue(), elementType));
		}

		array.visit(context);
		index.visit(context);

		visitor.visitInsn(Opcodes.DUP2);

		visitor.visitInsn(elementType.getOpcode(Opcodes.IALOAD));

		if(!prefix) visitor.visitInsn(elementType.getDupX2Opcode());

		elementType.generateAsInteger(1, context.getContext());

		if(increment) visitor.visitInsn(elementType.getOpcode(Opcodes.IADD));
		else visitor.visitInsn(elementType.getOpcode(Opcodes.ISUB));

		if(prefix) visitor.visitInsn(elementType.getDupX2Opcode());

		visitor.visitInsn(elementType.getOpcode(Opcodes.IASTORE));
	}

	private void property(FileContext context) throws SemanticException {
		Object[] lValueData = expression.getLValueData();

		Node obj = (Node) lValueData[0];
		Token name = (Token) lValueData[1];

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		obj.visit(context);

		WaterType objType = getObjectType(obj, context.getContext());

		if(!isStaticAccess) visitor.visitInsn(Opcodes.DUP);

		if(!objType.isObject()) {
			throw new SemanticException(name, "Cannot access member on type '%s'".formatted(objType));
		}

		if(objType.isNullable()) {
			throw new SemanticException(name, "Cannot use '.' to access members on a nullable type ('%s')".formatted(objType));
		}

		MemberAccessNode syntheticAccess = new MemberAccessNode(obj, name);

		WaterType type = syntheticAccess.getReturnType(context.getContext());
		if(!type.isNumeric()) {
			throw new SemanticException(operation, "Update expression ('%s') target must be numeric (got '%s')".formatted(operation.getValue(), type));
		}

		syntheticAccess.visitAccess(context);

		if(!prefix) {
			if(!isStaticAccess) {
				visitor.visitInsn(Opcodes.DUP_X1);
				visitor.visitInsn(type.getDupX1Opcode());
			}
			else {
				visitor.visitInsn(type.getDupOpcode());
			}
		}

		type.generateAsInteger(1, context.getContext());

		if(increment) visitor.visitInsn(type.getOpcode(Opcodes.IADD));
		else visitor.visitInsn(type.getOpcode(Opcodes.ISUB));

		if(prefix && isStaticAccess) {
			visitor.visitInsn(Opcodes.DUP);
		}

		AssignmentNode.handlePropertySettingLogic(obj, objType, name, type, context, operation, isStaticAccess, !(prefix && !isStaticAccess));

		if(!prefix && !isStaticAccess) {
			type.swap(objType, context.getContext().getMethodVisitor());
			visitor.visitInsn(Opcodes.POP);
		}
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
		WaterType returnType = expression.getReturnType(context);
		if(!returnType.isNumeric()) {
			throw new SemanticException(operation, "Update expression ('%s') target must be numeric (got '%s')".formatted(operation.getValue(), returnType));
		}
		return returnType;
	}

	@Override
	public String toString() {
		return prefix ? operation.getValue() + expression : expression + operation.getValue();
	}
}
