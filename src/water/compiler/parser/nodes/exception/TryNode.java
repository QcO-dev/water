package water.compiler.parser.nodes.exception;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.SemanticException;
import water.compiler.parser.Node;

import java.util.List;
import java.util.stream.Collectors;

public class TryNode implements Node {

	private final Node body;
	private final List<CatchNode> catchBlocks;

	public TryNode(Node body, List<CatchNode> catchBlocks) {
		this.body = body;
		this.catchBlocks = catchBlocks;
	}


	@Override
	public void visit(FileContext context) throws SemanticException {
		Label from = new Label();
		Label to = new Label();
		Label end = new Label();

		MethodVisitor visitor = context.getContext().getMethodVisitor();

		for(CatchNode catchNode : catchBlocks) {
			catchNode.updateMetadata(from, to);
			catchNode.generateTryCatchBlock(visitor, context.getContext());
		}

		visitor.visitLabel(from);
		visitor.visitInsn(Opcodes.NOP);

		body.visit(context);

		visitor.visitLabel(to);

		for(CatchNode catchNode : catchBlocks) {
			context.getContext().getMethodVisitor().visitJumpInsn(Opcodes.GOTO, end);
			catchNode.visit(context);
		}

		visitor.visitLabel(end);
	}

	@Override
	public String toString() {
		return "try %s%s".formatted(body, catchBlocks.stream().map(Node::toString).collect(Collectors.joining()));
	}
}
