package water.compiler.parser.nodes.value;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ArrayConstructorNode implements Node {

	public static class InitValue {
		public boolean isSubList;
		public ArrayList<InitValue> subValues;
		public Node value;

		public InitValue(Node value) {
			this.value = value;
			this.isSubList = false;
		}

		public InitValue(ArrayList<InitValue> subValues) {
			this.subValues = subValues;
			this.isSubList = true;
		}

		@Override
		public String toString() {
			return isSubList ? "❴%s❵".formatted(subValues.stream().map(InitValue::toString).collect(Collectors.joining(", "))) : value.toString();
		}
	}

	private final Token newToken;
	private final Node type;
	private final List<Node> dimensions;
	private final InitValue initValues;

	public ArrayConstructorNode(Token newToken, Node type, List<Node> dimensions, InitValue initValues) {
		this.newToken = newToken;
		this.type = type;
		this.dimensions = dimensions;
		this.initValues = initValues;
	}

	@Override
	public void visit(FileContext fileContext) throws SemanticException {
		Context context = fileContext.getContext();
		if(dimensions.get(0) == null && initValues == null) {
			throw new SemanticException(newToken, "Array must have first dimension initialized");
		}

		int size = 0;
		for(Node n : dimensions) {
			if(n == null) continue;
			if(!n.getReturnType(context).isInteger()) {
				throw new SemanticException(newToken, "Array size must be an integer");
			}
			n.visit(fileContext);
			size++;
		}

		WaterType elementType = getReturnType(context).getElementType();

		WaterType baseType = getReturnType(context);

		while(baseType.getElementType() != null) {
			baseType = baseType.getElementType();
		}

		MethodVisitor methodVisitor = context.getMethodVisitor();

		if(dimensions.get(0) == null) {
			TypeUtil.generateCorrectInt(initValues.subValues.size(), context);
		}

		if((size == 1 && dimensions.size() == 1) || initValues != null) {
			if(elementType.isPrimitive()) methodVisitor.visitIntInsn(Opcodes.NEWARRAY, elementType.primitiveToTType());
			else methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, elementType.getInternalName());
		}
		else if(size == 1) {
			methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY,
					WaterType.getArrayType(elementType, dimensions.size() - size, null).getDescriptor());
		}
		else {
			methodVisitor.visitMultiANewArrayInsn(getDescriptor(context), dimensions.size());
		}

		if(initValues != null) {
			compileInitializer(initValues, 0, methodVisitor, baseType, fileContext);
		}
	}

	private void compileInitializer(InitValue value, int dimension, MethodVisitor visitor, WaterType baseType, FileContext context) throws SemanticException {
		if(value.isSubList) {

			WaterType dataType = getReturnType(context.getContext()).getElementType();

			for(int i = 0; i < dimension; i++) {
				dataType = dataType.getElementType();
			}

			if(dimension > 0) {
				TypeUtil.generateCorrectInt(value.subValues.size(), context.getContext());

				if(dataType.isPrimitive()) {
					visitor.visitIntInsn(Opcodes.NEWARRAY, dataType.primitiveToTType());
				}
				else {
					visitor.visitTypeInsn(Opcodes.ANEWARRAY, dataType.getInternalName());
				}
			}

			for(int i = 0; i < value.subValues.size(); i++) {
				InitValue subValue = value.subValues.get(i);
				visitor.visitInsn(Opcodes.DUP);
				TypeUtil.generateCorrectInt(i, context.getContext());

				compileInitializer(subValue, dimension + 1, visitor, baseType, context);

				visitor.visitInsn(dataType.getOpcode(Opcodes.IASTORE));
			}
		}
		else {
			WaterType valueType = value.value.getReturnType(context.getContext());

			try {
				if(!baseType.isAssignableFrom(valueType, context.getContext(), true)) {
					throw new SemanticException(newToken, "Value of type '%s' cannot initialize array of type '%s'".formatted(
							valueType,
							baseType
					));
				}
			} catch (ClassNotFoundException e) {
				throw new SemanticException(newToken, "Could not resolve class '%s'".formatted(e.getMessage()));
			}
			value.value.visit(context);
		}
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		return WaterType.getArrayType(type.getReturnType(context), dimensions.size(), null);
	}

	private String getDescriptor(Context context) throws SemanticException {
		return "[".repeat(dimensions.size()) + type.getReturnType(context).getDescriptor();
	}

	@Override
	public String toString() {
		return "new %s%s %s".formatted(type,
				dimensions.stream().map(n -> n == null ? "" : n.toString()).map(d -> "[" + d + "]").collect(Collectors.joining()),
				initValues == null ? "" : initValues
		);
	}
}
