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

		// Remove double quotes
		char val = value.getValue().charAt(1);

		TypeUtil.generateCorrectInt(val, context.getContext());
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
