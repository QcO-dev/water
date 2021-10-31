package water.compiler.parser.nodes.operation;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

public class RelativeOperationNode implements Node {
	private final Node left;
	private final Token op;
	private final Node right;

	public RelativeOperationNode(Node left, Token op, Node right) {
		this.left = left;
		this.op = op;
		this.right = right;
		// Assert against incorrect token type, as fault of the parser.
		assert op.getType() == TokenType.LESS || op.getType() == TokenType.LESS_EQ || op.getType() == TokenType.GREATER || op.getType() == TokenType.GREATER_EQ;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Label falseL = new Label();
		Label end = new Label();
		generateConditional(context, falseL);

		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();

		methodVisitor.visitInsn(Opcodes.ICONST_1);
		methodVisitor.visitJumpInsn(Opcodes.GOTO, end);
		methodVisitor.visitLabel(falseL);
		methodVisitor.visitInsn(Opcodes.ICONST_0);
		methodVisitor.visitLabel(end);
	}

	public void generateConditional(FileContext context, Label falseL) throws SemanticException {
		WaterType leftType = left.getReturnType(context.getContext());
		WaterType rightType = right.getReturnType(context.getContext());

		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();

		if(leftType.isPrimitive() && rightType.isPrimitive()) {

			if(!leftType.isNumeric() || !rightType.isNumeric())
				throw new SemanticException(op,
						"Unsupported operation of '%s' between types '%s' and '%s'".formatted(op.getValue(),
								leftType, rightType));

			boolean same = leftType.equals(rightType);

			WaterType larger = leftType.getLarger(rightType);

			left.visit(context);

			if(!same && larger.equals(rightType)) {
				leftType.cast(rightType, methodVisitor);
			}

			right.visit(context);

			if(!same && larger.equals(leftType)) {
				rightType.cast(leftType, methodVisitor);
			}

			if(larger.isRepresentedAsInteger()) {
				switch (op.getType()) {
					case LESS -> generateIfBytecode(methodVisitor, Opcodes.IF_ICMPGE, falseL);
					case LESS_EQ -> generateIfBytecode(methodVisitor, Opcodes.IF_ICMPGT, falseL);
					case GREATER -> generateIfBytecode(methodVisitor, Opcodes.IF_ICMPLE, falseL);
					case GREATER_EQ -> generateIfBytecode(methodVisitor, Opcodes.IF_ICMPLT, falseL);
				}
			}
			else {
				larger.compareInit(methodVisitor);

				switch (op.getType()) {
					case LESS -> generateIfBytecode(methodVisitor, Opcodes.IFGE, falseL);
					case LESS_EQ -> generateIfBytecode(methodVisitor, Opcodes.IFGT, falseL);
					case GREATER -> generateIfBytecode(methodVisitor, Opcodes.IFLE, falseL);
					case GREATER_EQ -> generateIfBytecode(methodVisitor, Opcodes.IFLT, falseL);
				}
			}

		}
		else
			throw new SemanticException(op,
					"Unsupported operation of '%s' between types '%s' and '%s'".formatted(op.getValue(),
							leftType, rightType));
	}

	private void generateIfBytecode(MethodVisitor methodVisitor, int opcode, Label falseL) {
		methodVisitor.visitJumpInsn(opcode, falseL);
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		return WaterType.BOOLEAN_TYPE;
	}

	@Override
	public String toString() {
		return "%s %s %s".formatted(left, op.getValue(), right);
	}
}
