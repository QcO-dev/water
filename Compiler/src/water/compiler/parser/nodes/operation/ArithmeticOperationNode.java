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
import water.compiler.util.WaterType;

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
		WaterType leftType = left.getReturnType(context.getContext());
		WaterType rightType = right.getReturnType(context.getContext());

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		if(isConstant(context.getContext()) && resolvableConstantWithOptimizations(leftType, rightType, context)) {
			Object val = getConstantValue(context.getContext());
			TypeUtil.correctLdc(val, context.getContext());
		}

		else if(leftType.isPrimitive() && rightType.isPrimitive()) {
			WaterType larger = leftType.getLarger(rightType);

			boolean same = leftType.equals(rightType);

			left.visit(context);

			if(!same && larger.equals(rightType)) {
				leftType.cast(rightType, visitor);
			}

			right.visit(context);

			if(!same && larger.equals(leftType)) {
				rightType.cast(leftType, visitor);
			}

			visitor.visitInsn(larger.getOpcode(getOpcode()));

		}

		// Special string operations
		else if((leftType.equals(WaterType.STRING_TYPE) || rightType.equals(WaterType.STRING_TYPE)) && op.getType() == TokenType.PLUS) {
			concatStrings(context);
		}
		else if(leftType.equals(WaterType.STRING_TYPE) && rightType.isRepresentedAsInteger() && op.getType() == TokenType.STAR) {
			left.visit(context);
			right.visit(context);
			context.getContext().getMethodVisitor().visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "repeat", "(I)Ljava/lang/String;", false);
		}
		else if(leftType.isRepresentedAsInteger() && rightType.equals(WaterType.STRING_TYPE) && op.getType() == TokenType.STAR) {
			left.visit(context);
			right.visit(context);
			rightType.swap(leftType, context.getContext().getMethodVisitor());
			context.getContext().getMethodVisitor().visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "repeat", "(I)Ljava/lang/String;", false);
		}

		else throw new SemanticException(op, "Unsupported operation of '%s' between types '%s' and '%s'".formatted(op.getValue(), leftType, rightType));
	}

	private boolean resolvableConstantWithOptimizations(WaterType leftType, WaterType rightType, FileContext context) {
		return context.shouldOptimize("constant.arithmetic") && leftType.isNumeric() && rightType.isNumeric(); // Numerical arithmetic
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
	public WaterType getReturnType(Context context) throws SemanticException {
		WaterType leftType = left.getReturnType(context);
		WaterType rightType = right.getReturnType(context);

		if(leftType.equals(WaterType.STRING_TYPE) || rightType.equals(WaterType.STRING_TYPE))
			return WaterType.STRING_TYPE;

		return leftType.getLarger(rightType);
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
		WaterType leftReturnType = left.getReturnType(context);
		WaterType rightReturnType = right.getReturnType(context);

		try {
			if (!shouldBeConstant.contains(leftReturnType.toClass(context)) || !shouldBeConstant.contains(rightReturnType.toClass(context)))
				return false;
		}
		catch (ClassNotFoundException e) {
			throw new SemanticException(op, "Could not resolve class '%s'".formatted(e.getMessage()));
		}
		if((leftReturnType.equals(WaterType.STRING_TYPE) || rightReturnType.equals(WaterType.STRING_TYPE)) && op.getType() == TokenType.STAR) return false;
		return left.isConstant(context) && right.isConstant(context);
	}

	@Override
	public String toString() {
		String leftStr = left instanceof ArithmeticOperationNode ? "(" + left.toString() + ")" : left.toString();
		String rightStr = right instanceof ArithmeticOperationNode ? "(" + right.toString() + ")" : right.toString();

		return "%s %s %s".formatted(leftStr, op.getValue(), rightStr);
	}
}
