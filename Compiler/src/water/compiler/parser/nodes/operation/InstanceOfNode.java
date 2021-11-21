package water.compiler.parser.nodes.operation;

import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.compiler.Variable;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.variable.VariableAccessNode;
import water.compiler.util.Pair;
import water.compiler.util.WaterType;

public class InstanceOfNode implements Node {

	private final Node expression;
	private final Token op;
	private final Node type;
	private boolean shouldReScope;
	private Pair<Variable, WaterType> pastVariable;

	public InstanceOfNode(Node expression, Token op, Node type) {
		this.expression = expression;
		this.op = op;
		this.type = type;
		this.shouldReScope = false;
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

		if(expectedType.isNullable()) {
			throw new SemanticException(op, "Cannot check for an instance of a nullable type ('%s')".formatted(expectedType));
		}

		expression.visit(context);

		context.getContext().getMethodVisitor().visitTypeInsn(Opcodes.INSTANCEOF, expectedType.getInternalName());

		if(shouldReScope && expression instanceof VariableAccessNode) {
			Variable v = context.getContext().getScope().lookupVariable(((Token) expression.getLValueData()[0]).getValue());

			pastVariable = new Pair<>(v, v.getType());

			try {
				if(!v.getType().isAssignableFrom(expectedType, context.getContext(), false)
				&& !expectedType.isAssignableFrom(v.getType(), context.getContext(), false)) {
					throw new SemanticException(op, "Cannot check for an instance between '%s' and '%s'".formatted(v.getType(), expectedType));
				}
			} catch (ClassNotFoundException e) {
				throw new SemanticException(op, "Could not resolve class '%s'".formatted(e.getMessage()));
			}
			v.setType(expectedType);
		}
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		return WaterType.BOOLEAN_TYPE;
	}

	@Override
	public String toString() {
		return "%s instanceof %s".formatted(expression, type);
	}

	public void setShouldReScope(boolean shouldReScope) {
		this.shouldReScope = shouldReScope;
	}

	public Pair<Variable, WaterType> getPastVariable() {
		return pastVariable;
	}
}
