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
		StringBuilder builder = new StringBuilder();

		for(int i = 0; i < value.length(); i++) {
			if(value.charAt(i) == '\\') {
				i++;
				switch(value.charAt(i)) {
					case 't' -> builder.append('\t');
					case 'b' -> builder.append('\b');
					case 'n' -> builder.append('\n');
					case 'r' -> builder.append('\r');
					case 'f' -> builder.append('\f');
					case '\'' -> builder.append('\'');
					case '\"' -> builder.append('\"');
					default -> builder.append(value.charAt(i));
				}
			}
			else {
				builder.append(value.charAt(i));
			}
		}
		return builder.toString();
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		return WaterType.STRING_TYPE;
	}

	@Override
	public Object getConstantValue(Context context) {
		return escape(value.getValue().substring(1, value.getValue().length() - 1));
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
