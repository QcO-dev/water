package water.compiler.parser.nodes.value;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

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
		return WaterType.CHAR_TYPE;
	}

	@Override
	public Object getConstantValue(Context context) throws SemanticException {
		String val = value.getValue().substring(1, value.getValue().length() - 1);

		if(val.length() == 0 || (val.length() != 1 && val.charAt(0) != '\\') || (val.charAt(0) == '\\' && val.length() > 2)) {
			throw new SemanticException(value, "Character literal may only represent a single character");
		}

		val = escape(val);

		return val.charAt(0);
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
