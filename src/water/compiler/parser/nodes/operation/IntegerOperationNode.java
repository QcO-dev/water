package water.compiler.parser.nodes.operation;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

public class IntegerOperationNode implements Node {

	private final Node left;
	private final Token op;
	private final Node right;

	public IntegerOperationNode(Node left, Token op, Node right) {
		this.left = left;
		this.op = op;
		this.right = right;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Type leftType = left.getReturnType(context.getContext());
		Type rightType = right.getReturnType(context.getContext());

		if(!TypeUtil.isInteger(leftType) || !TypeUtil.isInteger(rightType)) {
			throw new SemanticException(op, "Unsupported operation of '%s' between types '%s' and '%s'".formatted(
					op.getValue(), TypeUtil.stringify(leftType), TypeUtil.stringify(rightType)
			));
		}

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		Type larger = TypeUtil.getLarger(leftType, rightType);

		boolean same = leftType.getSort() == rightType.getSort();

		left.visit(context);

		if(!same && larger.getSort() == rightType.getSort()) {
			TypeUtil.cast(visitor, leftType, rightType);
		}

		right.visit(context);

		if(!same && larger.getSort() == leftType.getSort()) {
			TypeUtil.cast(visitor, rightType, leftType);
		}

		visitor.visitInsn(larger.getOpcode(getOpcode()));
	}

	private int getOpcode() {
		return switch (op.getType()) {
			case BITWISE_OR -> Opcodes.IOR;
			case BITWISE_XOR -> Opcodes.IXOR;
			case BITWISE_AND -> Opcodes.IAND;
			default -> throw new IllegalStateException("Invalid operation of '%s' in IntegerOperationNode".formatted(op.getType()));
		};
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return TypeUtil.getLarger(left.getReturnType(context), right.getReturnType(context));
	}

	@Override
	public String toString() {
		return "%s %s %s".formatted(left, op.getValue(), right);
	}
}
