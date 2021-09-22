package water.compiler.parser.nodes.operation;

import org.objectweb.asm.Handle;
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

import java.util.List;

public class ArithmeticOperationNode implements Node {

	private final Node left;
	private final Token op;
	private final Node right;

	public ArithmeticOperationNode(Node left, Token op, Node right) {
		this.left = left;
		this.op = op;
		this.right = right;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Type leftType = left.getReturnType(context.getContext());
		Type rightType = right.getReturnType(context.getContext());

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		if(isConstant(context.getContext()) && resolvableConstantWithOptimizations(leftType, rightType, context)) {
			Object val = getConstantValue(context.getContext());
			TypeUtil.correctLdc(val, context.getContext());
		}

		else if(TypeUtil.isPrimitive(leftType) && TypeUtil.isPrimitive(rightType)) {
			Type larger = TypeUtil.getLarger(leftType, rightType);

			boolean same = leftType.getSort() == rightType.getSort();

			left.visit(context);

			if(!same && larger.getSort() == rightType.getSort()) {
				TypeUtil.cast(visitor, leftType, rightType);
			}

			right.visit(context);

			if(!same && larger.getSort() == leftType.getSort()) {
				TypeUtil.cast(visitor, rightType, leftType);
			}

			visitor.visitInsn(larger.getOpcode(getOpcode()));

		}

		// Special string operations
		else if((leftType.equals(TypeUtil.STRING_TYPE) || rightType.equals(TypeUtil.STRING_TYPE)) && op.getType() == TokenType.PLUS) {
			concatStrings(context);
		}
		else if(leftType.equals(TypeUtil.STRING_TYPE) && TypeUtil.isInteger(rightType) && op.getType() == TokenType.STAR) {
			left.visit(context);
			right.visit(context);
			context.getContext().getMethodVisitor().visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "repeat", "(I)Ljava/lang/String;", false);
		}
		else if(TypeUtil.isInteger(leftType) && rightType.equals(TypeUtil.STRING_TYPE) && op.getType() == TokenType.STAR) {
			left.visit(context);
			right.visit(context);
			TypeUtil.swap(context.getContext().getMethodVisitor(), rightType, leftType);
			context.getContext().getMethodVisitor().visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "repeat", "(I)Ljava/lang/String;", false);
		}

		else throw new SemanticException(op, "Unsupported operation of '%s' between types '%s' and '%s'".formatted(op.getValue(), TypeUtil.stringify(leftType), TypeUtil.stringify(rightType)));
	}

	private boolean resolvableConstantWithOptimizations(Type leftType, Type rightType, FileContext context) {
		return context.shouldOptimize("constant.arithmetic") && TypeUtil.isNumeric(leftType) && TypeUtil.isNumeric(rightType); // Numerical arithmetic
	}

	private void concatStrings(FileContext fc) throws SemanticException {
		Context context = fc.getContext();
		StringBuilder descriptor = new StringBuilder("(");
		StringBuilder recipe = new StringBuilder();

		concatStrings(left, right, descriptor, recipe, fc);

		descriptor.append(")Ljava/lang/String;");

		// No arguments - all constants
		if(fc.shouldOptimize("constant.string.concat") && recipe.indexOf("\u0001") == -1) {
			context.getMethodVisitor().visitLdcInsn(recipe.toString());
		}
		else {
			context.getMethodVisitor().visitInvokeDynamicInsn("makeConcatWithConstants", descriptor.toString(), new Handle(Opcodes.H_INVOKESTATIC,
					"java/lang/invoke/StringConcatFactory",
					"makeConcatWithConstants",
					"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
					false), recipe.toString());
		}
	}

	private void concatStrings(Node left, Node right, StringBuilder descriptor, StringBuilder recipe, FileContext fc) throws SemanticException {
		Context context = fc.getContext();
		if(left instanceof ArithmeticOperationNode && ((ArithmeticOperationNode) left).op.getType() == TokenType.PLUS) {
			ArithmeticOperationNode operation = (ArithmeticOperationNode) left;
			concatStrings(operation.left, operation.right, descriptor, recipe, fc);
		}

		else if(left.isConstant(context)) {
			recipe.append(left.getConstantValue(context));
		}
		else {
			descriptor.append(left.getReturnType(context).getDescriptor());
			recipe.append('\u0001');
			left.visit(fc);
		}

		if(right.isConstant(context)) {
			recipe.append(right.getConstantValue(context));
		}
		else {
			descriptor.append(right.getReturnType(context).getDescriptor());
			recipe.append('\u0001');
			right.visit(fc);
		}
	}

	private int getOpcode() {
		return switch (op.getType()) {
			case PLUS -> Opcodes.IADD;
			case MINUS -> Opcodes.ISUB;
			case STAR -> Opcodes.IMUL;
			case SLASH -> Opcodes.IDIV;
			case PERCENT -> Opcodes.IREM;
			default -> 0;
		};
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		Type leftType = left.getReturnType(context);
		Type rightType = right.getReturnType(context);

		if(leftType.equals(TypeUtil.STRING_TYPE) || rightType.equals(TypeUtil.STRING_TYPE))
			return TypeUtil.STRING_TYPE;

		return TypeUtil.getLarger(leftType, rightType);
	}

	@Override
	public Object getConstantValue(Context context) throws SemanticException {
		Object leftVal = left.getConstantValue(context);
		Object rightVal = right.getConstantValue(context);

		if((leftVal.getClass().equals(String.class) || rightVal.getClass().equals(String.class)) && op.getType() == TokenType.PLUS) {
			return leftVal.toString() + rightVal.toString();
		}

		if(leftVal.getClass().equals(Integer.class) && rightVal.getClass().equals(Integer.class)) {
			return intOp((int) leftVal, (int) rightVal);
		}
		double dLeft = (double) (leftVal.getClass().equals(Integer.class) ? ((Integer) leftVal).doubleValue() : leftVal);
		double dRight = (double) (rightVal.getClass().equals(Integer.class) ? ((Integer) rightVal).doubleValue() : rightVal);

		return doubleOp(dLeft, dRight);
	}

	private int intOp(int left, int right) {
		return switch (op.getType()) {
			case PLUS -> left + right;
			case MINUS -> left - right;
			case STAR -> left * right;
			case SLASH -> left / right;
			case PERCENT -> left % right;
			default -> 0;
		};
	}

	private double doubleOp(double left, double right) {
		return switch (op.getType()) {
			case PLUS -> left + right;
			case MINUS -> left - right;
			case STAR -> left * right;
			case SLASH -> left / right;
			case PERCENT -> left % right;
			default -> 0;
		};
	}

	@Override
	public boolean isConstant(Context context) throws SemanticException {
		List<Class<?>> shouldBeConstant = List.of(String.class, Integer.class, Double.class);
		Object leftReturnType = left.getReturnType(context);
		Type rightReturnType = right.getReturnType(context);

		if(!shouldBeConstant.contains(leftReturnType.getClass()) || !shouldBeConstant.contains(rightReturnType.getClass())) return false;
		if((leftReturnType.equals(TypeUtil.STRING_TYPE) || rightReturnType.equals(TypeUtil.STRING_TYPE)) && op.getType() == TokenType.STAR) return false;
		return left.isConstant(context) && right.isConstant(context);
	}

	@Override
	public String toString() {
		String leftStr = left instanceof ArithmeticOperationNode ? "(" + left.toString() + ")" : left.toString();
		String rightStr = right instanceof ArithmeticOperationNode ? "(" + right.toString() + ")" : right.toString();

		return "%s %s %s".formatted(leftStr, op.getValue(), rightStr);
	}
}
