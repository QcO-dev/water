package water.compiler.parser.nodes.exception;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

public class ThrowNode implements Node {
	private final Token throwTok;
	private final Node throwee;

	public ThrowNode(Token throwTok, Node throwee) {
		this.throwTok = throwTok;
		this.throwee = throwee;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Type throweeType = throwee.getReturnType(context.getContext());

		if(TypeUtil.isPrimitive(throweeType)) {
			throw new SemanticException(throwTok, "Cannot throw primitive type (got '%s').".formatted(TypeUtil.stringify(throweeType)));
		}

		try {
			if(!TypeUtil.isAssignableFrom(Type.getObjectType("java/lang/Throwable"), throweeType, context.getContext(), false)) {
				throw new SemanticException(throwTok, "throw target must be an extension of java.lang.Throwable ('%s' cannot be cast).".formatted(TypeUtil.stringify(throweeType)));
			}
		} catch (ClassNotFoundException e) {
			throw new SemanticException(throwTok, "Could not resolve class '%s'".formatted(e.getMessage()));
		}
		throwee.visit(context);
		context.getContext().getMethodVisitor().visitTypeInsn(Opcodes.CHECKCAST, throweeType.getInternalName());
		context.getContext().getMethodVisitor().visitInsn(Opcodes.ATHROW);

		context.getContext().getScope().setReturned(true);
	}

	@Override
	public String toString() {
		return "throw " + throwee + ";";
	}
}
