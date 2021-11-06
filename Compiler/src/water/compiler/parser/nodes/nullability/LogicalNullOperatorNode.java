package water.compiler.parser.nodes.nullability;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

public class LogicalNullOperatorNode implements Node {

	private final Node left;
	private final Token op;
	private final Node right;

	public LogicalNullOperatorNode(Node left, Token op, Node right) {
		this.left = left;
		this.op = op;
		this.right = right;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		WaterType leftType = left.getReturnType(context.getContext());

		if(!leftType.isNullable() || leftType.isPrimitive()) {
			throw new SemanticException(op, "Cannot perform '??' on a non-nullable type ('%s')".formatted(leftType));
		}

		left.visit(context);

		Label end = new Label();

		context.getContext().getMethodVisitor().visitInsn(leftType.getDupOpcode());
		context.getContext().getMethodVisitor().visitJumpInsn(Opcodes.IFNONNULL, end);

		context.getContext().getMethodVisitor().visitInsn(leftType.getPopOpcode());
		right.visit(context);

		context.getContext().getMethodVisitor().visitLabel(end);
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		WaterType leftType = left.getReturnType(context);
		WaterType rightType = right.getReturnType(context);

		if(!leftType.isNullable() || leftType.isPrimitive()) {
			throw new SemanticException(op, "Cannot perform '??' on a non-nullable type ('%s')".formatted(leftType));
		}

		WaterType nonNullableLeft = leftType.asNonNullable();

		try {
			if(!nonNullableLeft.isAssignableFrom(rightType, context, false)) {
				throw new SemanticException(op, "Cannot perform '??' on types '%s' and '%s'".formatted(leftType, rightType));
			}
		} catch (ClassNotFoundException e) {
			throw new SemanticException(op, "Cannot resolve class '%s'".formatted(e.getMessage()));
		}

		return nonNullableLeft;
	}

	@Override
	public String toString() {
		return "%s ?? %s".formatted(left, right);
	}
}
