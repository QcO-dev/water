package water.compiler.parser.nodes.value;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;

public class ThisNode implements Node {

	private final Token thisTok;

	public ThisNode(Token thisTok) {
		this.thisTok = thisTok;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {

		if(context.getContext().isStaticMethod()) {
			throw new SemanticException(thisTok, "Cannot access 'this' in a static context");
		}

		context.getContext().getMethodVisitor().visitVarInsn(Opcodes.ALOAD, 0);
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return Type.getObjectType(context.getCurrentClass());
	}

	@Override
	public String toString() {
		return "this";
	}
}
