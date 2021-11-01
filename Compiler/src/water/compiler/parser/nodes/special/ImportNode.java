package water.compiler.parser.nodes.special;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

public class ImportNode implements Node {
	private final Token importTok;
	private final Node type;

	public ImportNode(Token importTok, Node type) {
		this.importTok = importTok;
		this.type = type;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		// Do nothing
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		WaterType importType = type.getReturnType(context);

		if(importType.isPrimitive()) {
			throw new SemanticException(importTok, "Cannot import primitive type");
		}

		Class<?> klass;
		try {
			klass = Class.forName(importType.getClassName(), false, context.getLoader());
		} catch (ClassNotFoundException e) {
			throw new SemanticException(importTok, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		context.getImports().put(klass.getSimpleName(), importType.getClassName());
	}

	@Override
	public String toString() {
		return "import %s;".formatted(type);
	}
}
