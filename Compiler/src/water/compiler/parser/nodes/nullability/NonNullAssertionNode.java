package water.compiler.parser.nodes.nullability;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

public class NonNullAssertionNode implements Node {

	private final Node target;
	private final Token op;

	public NonNullAssertionNode(Node target, Token op) {
		this.target = target;
		this.op = op;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		target.visit(context);

		WaterType returnType = target.getReturnType(context.getContext());
		if(returnType.isPrimitive()) {
			throw new SemanticException(op, "Cannot assert non-null on primitive type '%s'".formatted(returnType));
		}
		if(!returnType.isNullable()) {
			throw new SemanticException(op, "Cannot assert non-null on type which is already not null ('%s')".formatted(returnType));
		}

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		visitor.visitInsn(returnType.getDupOpcode());

		Label nonNullBranch = new Label();

		visitor.visitJumpInsn(Opcodes.IFNONNULL, nonNullBranch);

		// Create NPE
		visitor.visitTypeInsn(Opcodes.NEW, "java/lang/NullPointerException");
		visitor.visitInsn(Opcodes.DUP);
		visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/NullPointerException", "<init>", "()V", false);

		// Throw
		visitor.visitInsn(Opcodes.ATHROW);

		visitor.visitLabel(nonNullBranch);
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		WaterType type = target.getReturnType(context);

		if(type.isPrimitive()) {
			throw new SemanticException(op, "Cannot assert non-null on primitive type '%s'".formatted(type));
		}
		if(!type.isNullable()) {
			throw new SemanticException(op, "Cannot assert non-null on type which is already not null ('%s')".formatted(type));
		}

		return type.copy().setNullable(false);
	}

	@Override
	public String toString() {
		return target + "!";
	}
}
