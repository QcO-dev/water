package water.compiler.parser.nodes.special;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

public class ImportNode implements Node {
	private final Token importTok;
	private final Node type;

	public ImportNode(Token importTok, Node type) {
		this.importTok = importTok;
		this.type = type;
	}

	@Override
	public void visit(FileContext fc) throws SemanticException {
		Context context = fc.getContext();
		Type importType = type.getReturnType(context);

		if(TypeUtil.isPrimitive(importType)) {
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
	public void preprocess(Context context) throws SemanticException {
		Type importType = Type.getObjectType(type.toString());

		if(TypeUtil.isPrimitive(importType)) {
			throw new SemanticException(importTok, "Cannot import primitive type");
		}

		String className = importType.getClassName();
		String simpleName = className.contains(".") ? className.substring(className.lastIndexOf('.')) : className;
		context.getImports().put(simpleName, className);
	}

	@Override
	public String toString() {
		return "import %s;".formatted(type);
	}
}
