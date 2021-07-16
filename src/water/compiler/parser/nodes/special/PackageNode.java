package water.compiler.parser.nodes.special;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.value.TypeNode;
import water.compiler.util.TypeUtil;

public class PackageNode implements Node {
	private final Token packageNode;
	private final TypeNode name;

	public PackageNode(Token packageNode, TypeNode name) {
		this.packageNode = packageNode;
		this.name = name;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		// Do nothing
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return name.getRawClassType();
	}

	@Override
	public String toString() {
		return "package %s;".formatted(name);
	}
}
