package water.compiler.parser.nodes.value;

import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

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

		val = escape(val);

		context.getContext().getMethodVisitor().visitLdcInsn(val);
	}

	private String escape(String value) {
		return value.replace("\\\\", "\\")
				.replace("\\t", "\t")
				.replace("\\b", "\b")
				.replace("\\n", "\n")
				.replace("\\r", "\r")
				.replace("\\f", "\f")
				.replace("\\'", "\'")
				.replace("\\\"", "\"");
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		return WaterType.STRING_TYPE;
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
