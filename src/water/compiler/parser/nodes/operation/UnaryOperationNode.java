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
import water.compiler.util.WaterType;

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

		WaterType expressionType = expression.getReturnType(context.getContext());

		MethodVisitor mv = context.getContext().getMethodVisitor();

		if(op.getType() == TokenType.EXCLAIM) {
			if(!expressionType.equals(WaterType.BOOLEAN_TYPE))
				throw new SemanticException(op, "Can only perform '!' on boolean values. (%s =/= boolean)".formatted(expressionType));

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
			if(!expressionType.isNumeric()) {
				throw new SemanticException(op, "Can only perform '-' on numeric values. (%s is not numeric)".formatted(expressionType));
			}


			mv.visitInsn(expressionType.getOpcode(Opcodes.INEG));
		}
		else if(op.getType() == TokenType.BITWISE_NOT) {
			if(!expressionType.isInteger()) {
				throw new SemanticException(op,
						"Can only perform '~' on integer values. ('%s' is not an integer)".formatted(expressionType));
			}

			if(expressionType.equals(WaterType.LONG_TYPE)) {
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
			WaterType returnType = expression.getReturnType(context);
			if(!returnType.equals(WaterType.BOOLEAN_TYPE))
				throw new SemanticException(op, "Con only perform '!' on boolean values. (%s =/= boolean)".formatted(returnType));

			boolean boolVal = (Boolean) value;

			return !boolVal;
		}
		else if(op.getType() == TokenType.MINUS) {
			WaterType returnType = expression.getReturnType(context);
			if(!returnType.isNumeric()) {
				throw new SemanticException(op, "Con only perform '-' on numeric values. (%s is not numeric)".formatted(returnType));
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
			WaterType type = expression.getReturnType(context);
			//TODO Refactor out of getRawType
			return switch(type.getRawType().getSort()) {
				case Type.DOUBLE, Type.FLOAT, Type.INT, Type.LONG -> true;
				default -> false;
			};
		}
		else if(op.getType() == TokenType.BITWISE_NOT) return false;
		return expression.isConstant(context);
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		return switch (op.getType()) {
			case EXCLAIM -> WaterType.BOOLEAN_TYPE;
			case MINUS, BITWISE_NOT -> expression.getReturnType(context);
			default -> throw new IllegalStateException("Unknown unary operator " + op.getType());
		};
	}

	@Override
	public String toString() {
		return op.getValue() + expression.toString();
	}
}
