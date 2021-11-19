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
			return isSubList ? "{%s}".formatted(subValues.stream().map(InitValue::toString).collect(Collectors.joining(", "))) : value.toString();
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

		WaterType elementType = type.getReturnType(context);

		MethodVisitor methodVisitor = context.getMethodVisitor();

		if(dimensions.get(0) == null) {
			TypeUtil.generateCorrectInt(initValues.subValues.size(), context);
		}

		if(size == 1 && dimensions.size() == 1) {
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
			for(int i = 0; i < initValues.subValues.size(); i++) {
				InitValue value = initValues.subValues.get(i);

				methodVisitor.visitInsn(getReturnType(context).getDupOpcode());
				TypeUtil.generateCorrectInt(i, context);

				value.value.visit(fileContext);

				methodVisitor.visitInsn(getReturnType(context).getElementType().getOpcode(Opcodes.IASTORE));
			}
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
