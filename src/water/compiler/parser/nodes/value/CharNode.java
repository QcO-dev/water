package water.compiler.parser.nodes.value;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

public class CharNode implements Node {

	private final Token value;

	public CharNode(Token value) {
		this.value = value;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		context.getContext().updateLine(value.getLine());

		// Remove quotes
		String val = value.getValue().substring(1, value.getValue().length() - 1);

		if(val.length() == 0 || (val.length() != 1 && val.charAt(0) != '\\') || (val.charAt(0) == '\\' && val.length() > 2)) {
			throw new SemanticException(value, "Character literal may only represent a single character");
		}

		val = escape(val);

		int code = val.charAt(0);

		TypeUtil.generateCorrectInt(code, context.getContext());
	}

	//TODO Remove Code duplication of StringNode
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
	public Type getReturnType(Context context) throws SemanticException {
		return Type.CHAR_TYPE;
	}

	@Override
	public Object getConstantValue(Context context) {
		return value.getValue().charAt(1);
	}

	@Override
	public boolean isConstant(Context context) throws SemanticException {
		return true;
	}

	@Override
	public String toString() {
		return value.getValue();
	}
}
