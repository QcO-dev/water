package water.compiler.parser.nodes.value;

import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

public class TypeNode implements Node {
	private final Token root;
	private final String path;
	private final boolean isPrimitive;
	private final int dimensions;
	private final TypeNode element;
	private boolean isNullable;

	public TypeNode(Token value) {
		this.root = value;
		this.path = null;
		this.isPrimitive = true;
		this.dimensions = 0;
		this.element = null;
		this.isNullable = false;
	}

	public TypeNode(Token root, String path) {
		this.root = root;
		this.path = path;
		this.isPrimitive = false;
		this.dimensions = 0;
		this.element = null;
		this.isNullable = false;
	}

	public TypeNode(TypeNode element, int dimensions) {
		this.root = element.root;
		this.path = null;
		this.isPrimitive = false;
		this.dimensions = dimensions;
		this.element = element;
		this.isNullable = false;
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		if(isNullable && isPrimitive) {
			throw new SemanticException(root, "Primitive types cannot be nullable.");
		}

		if(dimensions != 0) return WaterType.getType("[".repeat(dimensions) + element.getReturnType(context).getDescriptor()).asNullable(isNullable);

		if(isPrimitive) return switch (root.getType()) {
			case VOID -> WaterType.VOID_TYPE;
			case INT -> WaterType.INT_TYPE;
			case DOUBLE -> WaterType.DOUBLE_TYPE;
			case FLOAT -> WaterType.FLOAT_TYPE;
			case BOOLEAN -> WaterType.BOOLEAN_TYPE;
			case CHAR -> WaterType.CHAR_TYPE;
			case LONG -> WaterType.LONG_TYPE;
			case BYTE -> WaterType.BYTE_TYPE;
			case SHORT -> WaterType.SHORT_TYPE;
			default -> null;
		};

		try {
			return WaterType.getType(TypeUtil.classForName(path, context)).asNullable(isNullable);
		} catch (ClassNotFoundException e) {
			throw new SemanticException(root, "Could not resolve class '%s'".formatted(e.getMessage()));
		}
	}

	public WaterType getRawClassType() throws SemanticException {
		if(isPrimitive) throw new SemanticException(root, "Unexpected primitive type");

		return WaterType.getObjectType(path.replace('.', '/')).asNullable(isNullable);
	}

	public void setNullable(boolean isNullable) {
		this.isNullable = isNullable;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		// Do nothing
	}

	@Override
	public String toString() {
		if(dimensions != 0) return element.toString() + "[]".repeat(dimensions) + (isNullable ? "?" : "");

		return isPrimitive ? root.getValue() : path + (isNullable ? "?" : "");
	}
}
