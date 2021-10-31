package water.compiler.parser.nodes.value;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

/**
 * A bracketed expression - passes through all methods.
 * Used for cases such as:
 * <code>(12 + 2) + "Hello!"</code>
 * which without this node would produce:
 * <code>122Hello!</code>
 * and not:
 * <code>14Hello!</code>
 */
public class GroupingNode implements Node {
	private final Node value;

	public GroupingNode(Node value) {
		this.value = value;
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		return value.getReturnType(context);
	}

	@Override
	public Object getConstantValue(Context context) throws SemanticException {
		return value.getConstantValue(context);
	}

	@Override
	public boolean isConstant(Context context) throws SemanticException {
		return value.isConstant(context);
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		value.visit(context);
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		value.preprocess(context);
	}

	@Override
	public LValue getLValue() {
		return value.getLValue();
	}

	@Override
	public Object[] getLValueData() {
		return value.getLValueData();
	}

	@Override
	public String toString() {
		return "(%s)".formatted(value.toString());
	}
}
