package water.compiler.parser.nodes.classes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

public class SuperNode implements Node {

	private final Token superTok;

	public SuperNode(Token superTok) {
		this.superTok = superTok;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		context.getContext().updateLine(superTok.getLine());
		context.getContext().getMethodVisitor().visitVarInsn(Opcodes.ALOAD, 0);
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		if(context.getCurrentSuperClass() == null) {
			throw new SemanticException(superTok, "Can only use 'super' within a class.");
		}
		return context.getCurrentSuperClass();
	}

	@Override
	public String toString() {
		return "super";
	}
}
