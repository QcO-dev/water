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
				throw new SemanticException(op, "Can only perform '!' on boolean values. (%s =/= boolean)".formatted(TypeUtil.stringify(expressionType)));

			Label falseL = new Label();
			Label end = new Label();

			mv.visitJumpInsn(Opcodes.IFNE, falseL);
			mv.visitInsn(Opcodes.ICONST_1);
			mv.visitJumpInsn(Opcodes.GOTO, end);
			mv.visitLabel(falseL);
			mv.visitInsn(Opcodes.ICONST_0);
			mv.visitLabel(end);
		}
		else if(op.getType() == TokenType.MINUS) {
			if(!TypeUtil.isNumeric(expressionType)) {
				throw new SemanticException(op, "Can only perform '-' on numeric values. (%s is not numeric)".formatted(TypeUtil.stringify(expressionType)));
			}


			mv.visitInsn(expressionType.getOpcode(Opcodes.INEG));
		}
		else if(op.getType() == TokenType.BITWISE_NOT) {
			if(!TypeUtil.isInteger(expressionType)) {
				throw new SemanticException(op,
						"Can only perform '~' on integer values. ('%s' is not an integer)".formatted(TypeUtil.stringify(expressionType)));
			}

			if(expressionType.getSort() == Type.LONG) {
				TypeUtil.generateCorrectLong(-1, context.getContext());
				mv.visitInsn(Opcodes.LXOR);
			}
			else {
				TypeUtil.generateCorrectInt(-1, context.getContext());
				mv.visitInsn(Opcodes.IXOR);
			}
		}
	}

	@Override
	public Object getConstantValue(Context context) throws SemanticException {
		Object value = expression.getConstantValue(context);

		if(op.getType() == TokenType.EXCLAIM) {
			Type returnType = expression.getReturnType(context);
			if(returnType.getSort() != Type.BOOLEAN)
				throw new SemanticException(op, "Con only perform '!' on boolean values. (%s =/= boolean)".formatted(TypeUtil.stringify(returnType)));

			boolean boolVal = (Boolean) value;

			return !boolVal;
		}
		else if(op.getType() == TokenType.MINUS) {
			Type returnType = expression.getReturnType(context);
			if(!TypeUtil.isNumeric(returnType)) {
				throw new SemanticException(op, "Con only perform '-' on numeric values. (%s is not numeric)".formatted(TypeUtil.stringify(returnType)));
			}

			if(value instanceof Double) {
				return -(double)value;
			}
			else if(value instanceof Float) {
				return -(float)value;
			}
			else if(value instanceof Integer) {
				return -(int)value;
			}
			else if(value instanceof Long) {
				return -(long)value;
			}
		}

		throw new IllegalStateException("Unknown unary operator " + op.getType());
	}

	@Override
	public boolean isConstant(Context context) throws SemanticException {
		if(op.getType() == TokenType.MINUS) {
			Type type = expression.getReturnType(context);
			return switch(type.getSort()) {
				case Type.DOUBLE, Type.FLOAT, Type.INT, Type.LONG -> true;
				default -> false;
			};
		}
		else if(op.getType() == TokenType.BITWISE_NOT) return false;
		return expression.isConstant(context);
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return switch (op.getType()) {
			case EXCLAIM -> Type.BOOLEAN_TYPE;
			case MINUS, BITWISE_NOT -> expression.getReturnType(context);
			default -> throw new IllegalStateException("Unknown unary operator " + op.getType());
		};
	}

	@Override
	public String toString() {
		return op.getValue() + expression.toString();
	}
}
