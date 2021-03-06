package water.compiler.parser.nodes.operation;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

public class LogicalOperationNode implements Node {

	private final Node left;
	private final Token op;
	private final Node right;

	public LogicalOperationNode(Node left, Token op, Node right) {
		this.left = left;
		this.op = op;
		this.right = right;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		WaterType leftType = left.getReturnType(context.getContext());
		WaterType rightType = right.getReturnType(context.getContext());

		if(!verifyTypes(leftType, rightType)) {
			throw new SemanticException(op, "Unsupported operation of '%s' between types '%s' and '%s'".formatted(
					op.getValue(),
					leftType,
					rightType
			));
		}

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		if(op.getType() == TokenType.LOGICAL_AND) {
			Label falseL = new Label();
			Label end = new Label();

			left.visit(context);

			visitor.visitJumpInsn(Opcodes.IFEQ, falseL);

			right.visit(context);

			visitor.visitJumpInsn(Opcodes.IFEQ, falseL);

			visitor.visitInsn(Opcodes.ICONST_1);
			visitor.visitJumpInsn(Opcodes.GOTO, end);
			visitor.visitLabel(falseL);
			visitor.visitInsn(Opcodes.ICONST_0);
			visitor.visitLabel(end);
		}
		else if(op.getType() == TokenType.LOGICAL_OR) {
			Label trueL = new Label();
			Label falseL = new Label();
			Label end = new Label();

			left.visit(context);

			visitor.visitJumpInsn(Opcodes.IFNE, trueL);

			right.visit(context);

			visitor.visitJumpInsn(Opcodes.IFEQ, falseL);

			visitor.visitLabel(trueL);
			visitor.visitInsn(Opcodes.ICONST_1);
			visitor.visitJumpInsn(Opcodes.GOTO, end);

			visitor.visitLabel(falseL);
			visitor.visitInsn(Opcodes.ICONST_0);
			visitor.visitLabel(end);
		}
	}

	public boolean generateConditional(FileContext context, Label falseL) throws SemanticException {
		WaterType leftType = left.getReturnType(context.getContext());
		WaterType rightType = right.getReturnType(context.getContext());

		if(!verifyTypes(leftType, rightType)) {
			throw new SemanticException(op, "Unsupported operation of '%s' between types '%s' and '%s'".formatted(
					op.getValue(),
					leftType,
					rightType
			));
		}

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		if(op.getType() == TokenType.LOGICAL_AND) {
			left.visit(context);

			visitor.visitJumpInsn(Opcodes.IFEQ, falseL);

			right.visit(context);

			visitor.visitJumpInsn(Opcodes.IFEQ, falseL);
		}
		else if(op.getType() == TokenType.LOGICAL_OR) {
			Label end = new Label();
			left.visit(context);

			visitor.visitJumpInsn(Opcodes.IFNE, end);

			right.visit(context);

			visitor.visitJumpInsn(Opcodes.IFEQ, falseL);
			visitor.visitLabel(end);
		}
		return true;
	}

	private boolean verifyTypes(WaterType left, WaterType right) {
		return left.equals(WaterType.BOOLEAN_TYPE) && right.equals(WaterType.BOOLEAN_TYPE);
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
