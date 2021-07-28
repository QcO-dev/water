package water.compiler.parser.nodes.value;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

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
			if(!TypeUtil.isInteger(n.getReturnType(context))) {
				throw new SemanticException(newToken, "Array size must be an integer");
			}
			n.visit(fileContext);
		}

		Type elementType = type.getReturnType(context);

		MethodVisitor methodVisitor = context.getMethodVisitor();
		int size = dimensions.size();

		if(size == 1) {
			if(TypeUtil.isPrimitive(elementType)) methodVisitor.visitIntInsn(Opcodes.NEWARRAY, TypeUtil.primitiveToTType(elementType));
			else methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, elementType.getInternalName());
		}
		else {
			methodVisitor.visitMultiANewArrayInsn(getDescriptor(context), size);
		}
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return Type.getType(getDescriptor(context));
	}

	private String getDescriptor(Context context) throws SemanticException {
		return "[".repeat(dimensions.size()) + type.getReturnType(context).getDescriptor();
	}

	@Override
	public String toString() {
		return "new %s%s".formatted(type, dimensions.stream().map(Node::toString).map(d -> "[" + d + "]").collect(Collectors.joining()));
	}
}
