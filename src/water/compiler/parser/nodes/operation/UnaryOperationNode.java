package water.compiler.parser.nodes.operation;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

public class UnaryOperationNode implements Node {
	private final Token op;
	private final Node expression;

	public UnaryOperationNode(Token op, Node expression) {
		this.op = op;
		this.expression = expression;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {

		if(context.shouldOptimize("constant.unary") && isConstant(context.getContext())) {
			TypeUtil.correctLdc(getConstantValue(context.getContext()), context.getContext());
			return;
		}

		expression.visit(context);

		Type expressionType = expression.getReturnType(context.getContext());

		MethodVisitor mv = context.getContext().getMethodVisitor();

		if(op.getType() == TokenType.EXCLAIM) {
			if(expressionType.getSort() != Type.BOOLEAN)
				throw new SemanticException(op, "Con only perform '!' on boolean values. (%s =/= boolean)".formatted(TypeUtil.stringify(expressionType)));

			Label falseL = new Label();
			Label end = new Label();

			mv.visitJumpInsn(Opcodes.IFNE, falseL);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitJumpInsn(Opcodes.GOTO, end);
			mv.visitLabel(falseL);
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitLabel(end);
		}
		//TODO other unary operations
	}

	@Override
	public Object getConstantValue(Context context) throws SemanticException {
		Object value = expression.getConstantValue(context);

		if(op.getType() == TokenType.EXCLAIM) {
			Type returnType = expression.getReturnType(context);
			if(returnType.getSort() != Type.BOOLEAN)
				throw new SemanticException(op, "Con only perform '!' on boolean values. (%s =/= boolean)".formatted(TypeUtil.stringify(returnType)));

			boolean boolVal = ((Boolean) value).booleanValue();

			return !boolVal;
		}

		throw new IllegalStateException("Unknown unary operator " + op.getType());
	}

	@Override
	public boolean isConstant(Context context) throws SemanticException {
		return expression.isConstant(context);
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return switch (op.getType()) {
			case EXCLAIM -> Type.BOOLEAN_TYPE;
			default -> throw new IllegalStateException("Unknown unary operator " + op.getType());
		};
	}

	@Override
	public String toString() {
		return op.getValue() + expression.toString();
	}
}
