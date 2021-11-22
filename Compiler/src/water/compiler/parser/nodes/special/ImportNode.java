package water.compiler.parser.nodes.special;

import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

public record ImportNode(Token importTok, Node type, Token as) implements Node {

	@Override
	public void visit(FileContext context) throws SemanticException {
		// Do nothing
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		WaterType importType = type.getReturnType(context);

		if (importType.isPrimitive()) {
			throw new SemanticException(importTok, "Cannot import primitive type");
		}

		Class<?> klass;
		try {
			klass = Class.forName(importType.getClassName(), false, context.getLoader());
		} catch (ClassNotFoundException e) {
			throw new SemanticException(importTok, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		String name = as == null ? klass.getSimpleName() : as.getValue();
		context.getImports().put(name, importType.getClassName());
	}

	@Override
	public String toString() {
		return "import %s%s;".formatted(type,
				as == null ? "" : (" as " + as.getValue()));
	}
}
