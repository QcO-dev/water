package water.compiler.parser.nodes.statement;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.operation.EqualityOperationNode;
import water.compiler.parser.nodes.operation.LogicalOperationNode;
import water.compiler.parser.nodes.operation.RelativeOperationNode;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

public class WhileStatementNode implements Node {

	private final Token whileTok;
	private final Node condition;
	private final Node body;

	public WhileStatementNode(Token whileTok, Node condition, Node body) {
		this.whileTok = whileTok;
		this.condition = condition;
		this.body = body;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();

		WaterType conditionReturnType = condition.getReturnType(context.getContext());
		if(!conditionReturnType.equals(WaterType.BOOLEAN_TYPE)) {
			throw new SemanticException(whileTok, "Invalid condition type (%s =/= boolean)"
					.formatted(conditionReturnType));
		}

		context.getContext().updateLine(whileTok.getLine());

		Label conditionLabel = new Label();
		Label end = new Label();

		methodVisitor.visitLabel(conditionLabel);

		if(condition instanceof EqualityOperationNode) {
			if(!((EqualityOperationNode) condition).generateConditional(context, end)) {
				methodVisitor.visitJumpInsn(Opcodes.IFEQ, end);
			}
		}
		else if(condition instanceof RelativeOperationNode) {
			((RelativeOperationNode) condition).generateConditional(context, end);
		}
		else if(condition instanceof LogicalOperationNode) {
			((LogicalOperationNode) condition).generateConditional(context, end);
		}
		else {
			condition.visit(context);
			methodVisitor.visitJumpInsn(Opcodes.IFEQ, end);
		}

		body.visit(context);

		methodVisitor.visitJumpInsn(Opcodes.GOTO, conditionLabel);

		methodVisitor.visitLabel(end);
	}

	@Override
	public String toString() {
		return "while(%s) %s".formatted(condition, body);
	}
}
