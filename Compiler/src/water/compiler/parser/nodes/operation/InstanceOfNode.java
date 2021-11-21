package water.compiler.parser.nodes.operation;

import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

public class InstanceOfNode implements Node {

	private final Node expression;
	private final Token op;
	private final Node type;

	public InstanceOfNode(Node expression, Token op, Node type) {
		this.expression = expression;
		this.op = op;
		this.type = type;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		WaterType objectType = expression.getReturnType(context.getContext());

		if(!objectType.isObject()) {
			throw new SemanticException(op, "Can only perform 'instanceof' on objects (got '%s')".formatted(objectType));
		}

		WaterType expectedType = type.getReturnType(context.getContext());

		if(!expectedType.isObject()) {
			throw new SemanticException(op, "Cannot check for an instance of type '%s'".formatted(expectedType));
		}

		expression.visit(context);

		context.getContext().getMethodVisitor().visitTypeInsn(Opcodes.INSTANCEOF, expectedType.getInternalName());
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		return WaterType.BOOLEAN_TYPE;
	}

	@Override
	public String toString() {
		return "%s instanceof %s".formatted(expression, type);
	}
}
