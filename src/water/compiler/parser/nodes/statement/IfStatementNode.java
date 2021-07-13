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
import water.compiler.parser.nodes.operation.RelativeOperationNode;
import water.compiler.util.TypeUtil;

public class IfStatementNode implements Node {

	private final Token ifTok;
	private final Node condition;
	private final Node body;
	private final Node elseBody;

	public IfStatementNode(Token ifTok, Node condition, Node body, Node elseBody) {
		this.ifTok = ifTok;
		this.condition = condition;
		this.body = body;
		this.elseBody = elseBody;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();

		Type conditionReturnType = condition.getReturnType(context.getContext());
		if(conditionReturnType.getSort() != Type.BOOLEAN) {
			throw new SemanticException(ifTok, "Invalid condition type (%s =/= boolean)"
					.formatted(TypeUtil.stringify(conditionReturnType)));
		}

		context.getContext().updateLine(ifTok.getLine());

		Label falseL = new Label();
		Label end = new Label();

		if(condition instanceof EqualityOperationNode) {
			if(!((EqualityOperationNode) condition).generateConditional(context, falseL)) {
				methodVisitor.visitJumpInsn(Opcodes.IFEQ, falseL);
			}
		}
		else if(condition instanceof RelativeOperationNode) {
			((RelativeOperationNode) condition).generateConditional(context, falseL);
		}
		else {
			condition.visit(context);
			methodVisitor.visitJumpInsn(Opcodes.IFEQ, falseL);
		}

		body.visit(context);
		methodVisitor.visitJumpInsn(Opcodes.GOTO, end);
		methodVisitor.visitLabel(falseL);
		if(elseBody != null) {
			elseBody.visit(context);
		}
		methodVisitor.visitLabel(end);
	}

	@Override
	public String toString() {
		return "if(%s) %s".formatted(condition, body) + (elseBody == null ? "" : "else %s".formatted(elseBody));
	}
}
