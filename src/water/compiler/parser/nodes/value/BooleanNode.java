package water.compiler.parser.nodes.value;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;

public class BooleanNode implements Node {
	private final Token value;
	private boolean boolValue;

	public BooleanNode(Token value) {
		this.value = value;
		boolValue = Boolean.parseBoolean(value.getValue());
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		context.getContext().getMethodVisitor().visitInsn(boolValue ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return Type.BOOLEAN_TYPE;
	}

	@Override
	public Object getConstantValue(Context context) {
		return boolValue;
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
