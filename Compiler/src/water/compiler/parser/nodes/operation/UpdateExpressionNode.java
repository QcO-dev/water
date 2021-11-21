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
import water.compiler.util.WaterType;

public class UpdateExpressionNode implements Node {

	private Node expression;
	private Token operation;
	private boolean prefix;
	private boolean increment;

	public UpdateExpressionNode(Node expression, Token operation, boolean prefix) {
		this.expression = expression;
		this.operation = operation;
		this.prefix = prefix;
		this.increment = operation.getType() == TokenType.INCREMENT;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		if(expression.getLValue() == LValue.NONE) {
			throw new SemanticException(operation, "Invalid lvalue - cannot perform operation");
		}

		switch (expression.getLValue()) {
			case VARIABLE -> variable(context);
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

		if(!variable.getType().isNumeric()) {
			throw new SemanticException(operation, "Update expression ('%s') target must be numeric".formatted(operation.getValue()));
		}

		WaterType type = variable.getType();

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		if(variable.getVariableType() == VariableType.LOCAL && type.getSort() == WaterType.Sort.INT) {
			int value = increment ? 1 : -1;

			if(!prefix) visitor.visitVarInsn(Opcodes.ILOAD, variable.getIndex());

			visitor.visitIincInsn(variable.getIndex(), value);

			if(prefix) visitor.visitVarInsn(Opcodes.ILOAD, variable.getIndex());

			return;
		}

		//TODO OTHER VARIABLE TYPES
		switch (variable.getVariableType()) {
			case LOCAL -> visitor.visitVarInsn(type.getOpcode(Opcodes.ILOAD), variable.getIndex());
		}

		if(!prefix) visitor.visitInsn(type.getDupOpcode());

		type.generateAsInteger(1, context.getContext());

		if(increment) visitor.visitInsn(type.getOpcode(Opcodes.IADD));
		else visitor.visitInsn(type.getOpcode(Opcodes.ISUB));

		if(prefix) visitor.visitInsn(type.getDupOpcode());

		//TODO OTHER VARIABLE TYPES
		switch (variable.getVariableType()) {
			case LOCAL -> visitor.visitVarInsn(type.getOpcode(Opcodes.ISTORE), variable.getIndex());
		}

	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		WaterType returnType = expression.getReturnType(context);
		if(!returnType.isNumeric()) {
			throw new SemanticException(operation, "Update expression ('%s') target must be numeric".formatted(operation.getValue()));
		}
		return returnType;
	}

	@Override
	public String toString() {
		return prefix ? operation.getValue() + expression : expression + operation.getValue();
	}
}