package water.compiler.parser.nodes.operation;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

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
		Type leftType = left.getReturnType(context.getContext());
		Type indexType = index.getReturnType(context.getContext());

		if(leftType.getSort() != Type.ARRAY) {
			throw new SemanticException(bracket, "Cannot get index of type '%s'".formatted(TypeUtil.stringify(leftType)));
		}
		if(!TypeUtil.isInteger(indexType)) {
			throw new SemanticException(bracket, "Index must be an integer type (got '%s')".formatted(TypeUtil.stringify(indexType)));
		}

		left.visit(context);
		index.visit(context);

		context.getContext().getMethodVisitor().visitInsn(leftType.getElementType().getOpcode(Opcodes.IALOAD));
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		Type leftType = left.getReturnType(context);

		if(leftType.getSort() != Type.ARRAY) {
			throw new SemanticException(bracket, "Cannot get index of type '%s'".formatted(TypeUtil.stringify(leftType)));
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