package water.compiler.parser.nodes.value;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

public class StringNode implements Node {
	private final Token value;

	public StringNode(Token value) {
		this.value = value;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		context.getContext().updateLine(value.getLine());
		// Remove double quotes
		String val = value.getValue().substring(1, value.getValue().length() - 1);

		context.getContext().getMethodVisitor().visitLdcInsn(val);
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return TypeUtil.STRING_TYPE;
	}

	@Override
	public Object getConstantValue(Context context) {
		return value.getValue().substring(1, value.getValue().length() - 1);
	}

	@Override
	public boolean isConstant(Context context) {
		return true;
	}

	@Override
	public String toString() {
		return value.getValue();
	}
}
