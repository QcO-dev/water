package water.compiler.parser.nodes.operation;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

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
		WaterType leftType = left.getReturnType(context.getContext());
		WaterType rightType = right.getReturnType(context.getContext());

		if(!leftType.isInteger() || !rightType.isInteger()) {
			throw new SemanticException(op, "Unsupported operation of '%s' between types '%s' and '%s'".formatted(
					op.getValue(), leftType, rightType
			));
		}

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		WaterType larger = leftType.getLarger(rightType);

		boolean same = leftType.equals(rightType);

		left.visit(context);

		if(!same && larger.equals(rightType)) {
			leftType.cast(rightType, visitor);
		}

		right.visit(context);

		if(!same && larger.equals(leftType)) {
			rightType.cast(leftType, visitor);
		}

		visitor.visitInsn(larger.getOpcode(getOpcode()));
	}

	private int getOpcode() {
		return switch (op.getType()) {
			case BITWISE_OR -> Opcodes.IOR;
			case BITWISE_XOR -> Opcodes.IXOR;
			case BITWISE_AND -> Opcodes.IAND;
			case BITWISE_SHL -> Opcodes.ISHL;
			case BITWISE_SHR -> Opcodes.ISHR;
			case BITWISE_USHR -> Opcodes.IUSHR;
			default -> throw new IllegalStateException("Invalid operation of '%s' in IntegerOperationNode".formatted(op.getType()));
		};
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		return left.getReturnType(context).getLarger(right.getReturnType(context));
	}

	@Override
	public String toString() {
		return "%s %s %s".formatted(left, op.getValue(), right);
	}
}
