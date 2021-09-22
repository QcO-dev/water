package water.compiler.parser.nodes.value;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

public class TypeNode implements Node {
	private final Token root;
	private final String path;
	private final boolean isPrimitive;
	private final int dimensions;
	private final TypeNode element;

	public TypeNode(Token value) {
		this.root = value;
		this.path = null;
		this.isPrimitive = true;
		this.dimensions = 0;
		this.element = null;
	}

	public TypeNode(Token root, String path) {
		this.root = root;
		this.path = path;
		this.isPrimitive = false;
		this.dimensions = 0;
		this.element = null;
	}

	public TypeNode(TypeNode element, int dimensions) {
		this.root = element.root;
		this.path = null;
		this.isPrimitive = false;
		this.dimensions = dimensions;
		this.element = element;
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {

		if(dimensions != 0) return Type.getType("[".repeat(dimensions) + element.getReturnType(context).getDescriptor());

		if(isPrimitive) return switch (root.getType()) {
			case VOID -> Type.VOID_TYPE;
			case INT -> Type.INT_TYPE;
			case DOUBLE -> Type.DOUBLE_TYPE;
			case FLOAT -> Type.FLOAT_TYPE;
			case BOOLEAN -> Type.BOOLEAN_TYPE;
			case CHAR -> Type.CHAR_TYPE;
			case LONG -> Type.LONG_TYPE;
			case BYTE -> Type.BYTE_TYPE;
			case SHORT -> Type.SHORT_TYPE;
			default -> null;
		};

		try {
			return Type.getType(TypeUtil.classForName(path, context));
		} catch (ClassNotFoundException e) {
			throw new SemanticException(root, "Could not resolve class '%s'".formatted(e.getMessage()));
		}
	}

	public Type getRawClassType() throws SemanticException {
		if(isPrimitive) throw new SemanticException(root, "Unexpected primitive type");

		return Type.getObjectType(path.replace('.', '/'));
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		// Do nothing
	}

	@Override
	public String toString() {
		if(dimensions != 0) return element.toString() + "[]".repeat(dimensions);

		return isPrimitive ? root.getValue() : path;
	}
}
