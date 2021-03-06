package water.compiler.parser.nodes.nullability;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.operation.IndexAccessNode;
import water.compiler.util.WaterType;

public class NullableIndexAccessNode implements Node {

	private final Token bracket;
	private final Node left;
	private final Node index;

	public NullableIndexAccessNode(Token bracket, Node left, Node index) {
		this.bracket = bracket;
		this.left = left;
		this.index = index;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		WaterType returnType = left.getReturnType(context.getContext());
		if(!returnType.isNullable()) {
			throw new SemanticException(bracket, "Cannot use '?[' on non-nullable type ('%s')".formatted(left.getReturnType(context.getContext())));
		}

		Label nullValue = new Label();

		boolean isSettingJump = false;
		if(context.getContext().getNullJumpLabel(nullValue) == nullValue) {
			context.getContext().setNullJumpLabel(nullValue);
			isSettingJump = true;
		}

		left.visit(context);

		if(isSettingJump) {
			context.getContext().setNullJumpLabel(null);
		}

		context.getContext().getMethodVisitor().visitInsn(Opcodes.DUP);
		context.getContext().getMethodVisitor().visitJumpInsn(Opcodes.IFNULL, context.getContext().getNullJumpLabel(nullValue));

		synthetic().visitAccess(context);

		synthetic().getReturnType(context.getContext()).autoBox(context.getContext().getMethodVisitor());

		context.getContext().getMethodVisitor().visitLabel(nullValue);
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		WaterType rawType = synthetic().getReturnType(context);

		return rawType.getAutoBoxWrapper().asNullable();
	}

	private IndexAccessNode synthetic() {
		return new IndexAccessNode(bracket, left, index);
	}

	@Override
	public LValue getLValue() {
		return LValue.NULLABLE_ARRAY;
	}

	@Override
	public Object[] getLValueData() {
		return new Object[] { left, index, bracket };
	}

	@Override
	public String toString() {
		return "%s?[%s]".formatted(left, index);
	}
}
