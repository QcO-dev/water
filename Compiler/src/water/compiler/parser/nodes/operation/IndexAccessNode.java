package water.compiler.parser.nodes.operation;

import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

public class IndexAccessNode implements Node {
	private final Token bracket;
	private final Node left;
	private final Node index;

	public IndexAccessNode(Token bracket, Node left, Node index) {
		this.bracket = bracket;
		this.left = left;
		this.index = index;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		left.visit(context);

		WaterType returnType = left.getReturnType(context.getContext());
		if(returnType.isNullable()) {
			throw new SemanticException(bracket, "Cannot use '[' to call methods on a nullable type ('%s')".formatted(returnType));
		}

		visitAccess(context);
	}

	public void visitAccess(FileContext context) throws SemanticException {
		WaterType leftType = left.getReturnType(context.getContext());
		WaterType indexType = index.getReturnType(context.getContext());

		if(!leftType.isArray()) {
			throw new SemanticException(bracket, "Cannot get index of type '%s'".formatted(leftType));
		}
		if(!indexType.isInteger()) {
			throw new SemanticException(bracket, "Index must be an integer type (got '%s')".formatted(indexType));
		}

		index.visit(context);

		context.getContext().getMethodVisitor().visitInsn(leftType.getElementType().getOpcode(Opcodes.IALOAD));
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		WaterType leftType = left.getReturnType(context);

		if(!leftType.isArray()) {
			throw new SemanticException(bracket, "Cannot get index of type '%s'".formatted(leftType));
		}

		return leftType.getElementType();
	}

	@Override
	public LValue getLValue() {
		return LValue.ARRAY;
	}

	@Override
	public Object[] getLValueData() {
		return new Object[] { left, index };
	}

	@Override
	public String toString() {
		return "%s[%s]".formatted(left, index);
	}
}
