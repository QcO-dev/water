package water.compiler.parser.nodes.nullability;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.classes.MethodCallNode;
import water.compiler.util.WaterType;

import java.util.List;
import java.util.stream.Collectors;

public class NullableMethodCallNode implements Node {

	private final Node left;
	private final Token name;
	private final List<Node> args;
	private final boolean isSuper;

	public NullableMethodCallNode(Node left, Token name, List<Node> args, boolean isSuper) {
		this.left = left;
		this.name = name;
		this.args = args;
		this.isSuper = isSuper;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		WaterType returnType = left.getReturnType(context.getContext());
		if(!returnType.isNullable()) {
			throw new SemanticException(name, "Cannot use '?.' on non-nullable type ('%s')".formatted(left.getReturnType(context.getContext())));
		}

		Label nullValue = new Label();
		left.visit(context);

		context.getContext().getMethodVisitor().visitInsn(Opcodes.DUP);
		context.getContext().getMethodVisitor().visitJumpInsn(Opcodes.IFNULL, nullValue);

		synthetic().visitCall(context);
		synthetic().getReturnType(context.getContext()).autoBox(context.getContext().getMethodVisitor());

		context.getContext().getMethodVisitor().visitLabel(nullValue);
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		WaterType rawType = synthetic().getReturnType(context);

		return rawType.getAutoBoxWrapper().asNullable();
	}

	private MethodCallNode synthetic() {
		return new MethodCallNode(left, name, args, isSuper);
	}

	@Override
	public String toString() {
		return "%s?.%s(%s)".formatted(left, name.getValue(), args.stream().map(Node::toString).collect(Collectors.joining(", ")));
	}
}
