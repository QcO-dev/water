package water.compiler.parser.nodes.statement;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Scope;
import water.compiler.compiler.SemanticException;
import water.compiler.compiler.Variable;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.operation.EqualityOperationNode;
import water.compiler.parser.nodes.operation.InstanceOfNode;
import water.compiler.parser.nodes.operation.LogicalOperationNode;
import water.compiler.parser.nodes.operation.RelativeOperationNode;
import water.compiler.util.Pair;
import water.compiler.util.WaterType;

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

		WaterType conditionReturnType = condition.getReturnType(context.getContext());
		if(!conditionReturnType.equals(WaterType.BOOLEAN_TYPE)) {
			throw new SemanticException(ifTok, "Invalid condition type (%s =/= boolean)"
					.formatted(conditionReturnType));
		}

		context.getContext().updateLine(ifTok.getLine());

		Label falseL = new Label();
		Label end = new Label();

		boolean instanceofNode = false;
		if(condition instanceof InstanceOfNode) {
			((InstanceOfNode) condition).setShouldReScope(true);
			instanceofNode = true;
		}

		if(condition instanceof EqualityOperationNode) {
			if(!((EqualityOperationNode) condition).generateConditional(context, falseL)) {
				methodVisitor.visitJumpInsn(Opcodes.IFEQ, falseL);
			}
		}
		else if(condition instanceof RelativeOperationNode) {
			((RelativeOperationNode) condition).generateConditional(context, falseL);
		}
		else if(condition instanceof LogicalOperationNode) {
			((LogicalOperationNode) condition).generateConditional(context, falseL);
		}
		else {
			condition.visit(context);
			methodVisitor.visitJumpInsn(Opcodes.IFEQ, falseL);
		}

		body.visit(context);

		if(instanceofNode) {
			Pair<Variable, WaterType> pastVariable = ((InstanceOfNode) condition).getPastVariable();
			pastVariable.getFirst().setType(pastVariable.getSecond());
		}

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
