package water.compiler.parser.nodes.value;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

import java.util.List;
import java.util.stream.Collectors;

public class ArrayConstructorNode implements Node {
	private final Token newToken;
	private final Node type;
	private final List<Node> dimensions;

	public ArrayConstructorNode(Token newToken, Node type, List<Node> dimensions) {
		this.newToken = newToken;
		this.type = type;
		this.dimensions = dimensions;
	}

	@Override
	public void visit(FileContext fileContext) throws SemanticException {
		Context context = fileContext.getContext();
		for(Node n : dimensions) {
			if(!n.getReturnType(context).isInteger()) {
				throw new SemanticException(newToken, "Array size must be an integer");
			}
			n.visit(fileContext);
		}

		WaterType elementType = type.getReturnType(context);

		MethodVisitor methodVisitor = context.getMethodVisitor();
		int size = dimensions.size();

		if(size == 1) {
			if(elementType.isPrimitive()) methodVisitor.visitIntInsn(Opcodes.NEWARRAY, elementType.primitiveToTType());
			else methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, elementType.getInternalName());
		}
		else {
			methodVisitor.visitMultiANewArrayInsn(getDescriptor(context), size);
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
		return "new %s%s".formatted(type, dimensions.stream().map(Node::toString).map(d -> "[" + d + "]").collect(Collectors.joining()));
	}
}
