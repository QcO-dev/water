package water.compiler.parser.nodes.nullability;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.classes.MemberAccessNode;
import water.compiler.util.WaterType;

public class NullableMemberAccessNode implements Node {

	private final Node left;
	private final Token name;

	public NullableMemberAccessNode(Node left, Token name) {
		this.left = left;
		this.name = name;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		WaterType returnType = left.getReturnType(context.getContext());
		if(!returnType.isNullable()) {
			throw new SemanticException(name, "Cannot use '?.' on non-nullable type ('%s')".formatted(left.getReturnType(context.getContext())));
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

	private MemberAccessNode synthetic() {
		return new MemberAccessNode(left, name);
	}

	@Override
	public LValue getLValue() {
		return LValue.NULLABLE_PROPERTY;
	}

	@Override
	public Object[] getLValueData() {
		return new Object[] { left, name };
	}

	@Override
	public String toString() {
		return "%s?.%s".formatted(left, name.getValue());
	}
}
