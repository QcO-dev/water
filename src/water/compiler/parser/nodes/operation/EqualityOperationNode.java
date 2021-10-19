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

public class EqualityOperationNode implements Node {

	private final Node left;
	private final Token op;
	private final Node right;

	public EqualityOperationNode(Node left, Token op, Node right) {
		this.left = left;
		this.op = op;
		this.right = right;
		// Assert against incorrect token type, as fault of the parser.
		assert op.getType() == TokenType.EQEQ || op.getType() == TokenType.EXEQ || op.getType() == TokenType.TRI_EQ || op.getType() == TokenType.TRI_EXEQ;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Label falseL = new Label();
		Label end = new Label();

		if(generateConditional(context, falseL)) {
			MethodVisitor methodVisitor = context.getContext().getMethodVisitor();

			methodVisitor.visitInsn(Opcodes.ICONST_1);
			methodVisitor.visitJumpInsn(Opcodes.GOTO, end);
			methodVisitor.visitLabel(falseL);
			methodVisitor.visitInsn(Opcodes.ICONST_0);
			methodVisitor.visitLabel(end);
		}
	}

	public boolean generateConditional(FileContext context, Label falseL) throws SemanticException {
		Type leftType = left.getReturnType(context.getContext());
		Type rightType = right.getReturnType(context.getContext());

		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();

		if(TypeUtil.isPrimitive(leftType) && TypeUtil.isPrimitive(rightType)) {
			boolean same = leftType.getSort() == rightType.getSort();

			Type larger = TypeUtil.getLarger(leftType, rightType);

			left.visit(context);

			if(!same && larger.getSort() == rightType.getSort()) {
				TypeUtil.cast(methodVisitor, leftType, rightType);
			}

			right.visit(context);

			if(!same && larger.getSort() == leftType.getSort()) {
				TypeUtil.cast(methodVisitor, rightType, leftType);
			}

			if(TypeUtil.isInteger(larger) && larger.getSize() != 2) {
				switch (op.getType()) {
					case EQEQ -> generateIfBytecode(methodVisitor, Opcodes.IF_ICMPNE, falseL);
					case EXEQ -> generateIfBytecode(methodVisitor, Opcodes.IF_ICMPEQ, falseL);
					case TRI_EQ, TRI_EXEQ -> throw new SemanticException(op,
							"Cannot perform address comparison on primitives ('%s', '%s')"
									.formatted(TypeUtil.stringify(leftType), TypeUtil.stringify(rightType)));
				}
			}
			else {
				TypeUtil.compareInit(methodVisitor, larger);

				switch (op.getType()) {
					case EQEQ -> generateIfBytecode(methodVisitor, Opcodes.IFNE, falseL);
					case EXEQ -> generateIfBytecode(methodVisitor, Opcodes.IFEQ, falseL);
					case TRI_EQ, TRI_EXEQ -> throw new SemanticException(op,
							"Cannot perform address comparison on primitives ('%s', '%s')"
									.formatted(TypeUtil.stringify(leftType), TypeUtil.stringify(rightType)));
				}
			}
			return true;
		}
		else if(!TypeUtil.isPrimitive(leftType) && !TypeUtil.isPrimitive(rightType)) {
			left.visit(context);
			right.visit(context);
			switch (op.getType()) {
				case EQEQ:
					isEqual(methodVisitor, leftType);
					return false;
				case EXEQ:
					isEqual(methodVisitor, leftType);
					not(methodVisitor, falseL);
					return true;
				case TRI_EQ:
					generateIfBytecode(methodVisitor, Opcodes.IF_ACMPNE, falseL);
					return true;
				case TRI_EXEQ:
					generateIfBytecode(methodVisitor, Opcodes.IF_ACMPEQ, falseL);
					return true;
			}
		}
		else {
			// Due to the above if statements we can presume we have a pair of operands where one is an object and one is a primitive

			left.visit(context);
			leftType = TypeUtil.autoBox(methodVisitor, leftType);

			right.visit(context);
			TypeUtil.autoBox(methodVisitor, rightType);

			switch (op.getType()) {
				case EQEQ -> isEqual(methodVisitor, leftType);
				case EXEQ -> {
					isEqual(methodVisitor, leftType);
					not(methodVisitor, falseL);
				}
				case TRI_EQ, TRI_EXEQ -> throw new SemanticException(op,
						"Cannot perform address comparison on types ('%s', '%s')"
								.formatted(TypeUtil.stringify(leftType), TypeUtil.stringify(rightType)));
			}
		}
		return false;
	}

	private void isEqual(MethodVisitor methodVisitor, Type owner) {
		methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner.getInternalName(), "equals", "(Ljava/lang/Object;)Z", false);
	}

	private void not(MethodVisitor methodVisitor, Label falseL) {
		methodVisitor.visitJumpInsn(Opcodes.IFNE, falseL);
	}

	private void generateIfBytecode(MethodVisitor methodVisitor, int opcode, Label falseL) {
		methodVisitor.visitJumpInsn(opcode, falseL);
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return Type.BOOLEAN_TYPE;
	}

	@Override
	public String toString() {
		return "%s %s %s".formatted(left, op.getValue(), right);
	}
}
