package water.compiler.parser.nodes.block;

import water.compiler.FileContext;
import water.compiler.compiler.Scope;
import water.compiler.compiler.SemanticException;
import water.compiler.parser.Node;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a scoped block - '{}'
 */
public class BlockNode implements Node {

	private final List<Node> body;

	public BlockNode(List<Node> body) {
		this.body = body;
	}

	@Override
	public String toString() {
		return "{" + body.stream().map(Node::toString).collect(Collectors.joining()) + "}";
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Scope outer = context.getContext().getScope();

		// increment scope
		context.getContext().setScope(outer.nextDepth());
		for(Node n : body) {
			n.visit(context);
		}
		outer.setReturned(context.getContext().getScope().isReturned());
		context.getContext().setScope(outer);
	}
}
