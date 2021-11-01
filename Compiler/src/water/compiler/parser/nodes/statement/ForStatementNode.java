package water.compiler.parser.nodes.statement;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Scope;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.operation.EqualityOperationNode;
import water.compiler.parser.nodes.operation.LogicalOperationNode;
import water.compiler.parser.nodes.operation.RelativeOperationNode;
import water.compiler.util.OptimizationUtil;
import water.compiler.util.WaterType;

public class ForStatementNode implements Node {

	private final Token forTok;
	private final Node init;
	private final Node condition;
	private final Node iterate;
	private final Node body;

	public ForStatementNode(Token forTok, Node init, Node condition, Node iterate, Node body) {
		this.forTok = forTok;
		this.init = init;
		this.condition = condition;
		this.iterate = iterate;
		this.body = body;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		WaterType conditionReturnType = condition.getReturnType(context.getContext());
		if(!conditionReturnType.equals(WaterType.BOOLEAN_TYPE)) {
			throw new SemanticException(forTok, "Invalid condition type (%s =/= boolean)"
					.formatted(conditionReturnType));
		}

		Scope outer = context.getContext().getScope();

		// increment scope
		context.getContext().setScope(outer.nextDepth());

		init.visit(context);

		Label conditionL = new Label();
		Label end = new Label();

		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();
		methodVisitor.visitLabel(conditionL);

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

		boolean varAssign = OptimizationUtil.assignmentNodeExpressionEval(iterate, context);

		WaterType iterateType = iterate.getReturnType(context.getContext());

		if(!iterateType.equals(WaterType.VOID_TYPE) && !varAssign) methodVisitor.visitInsn(iterateType.getPopOpcode());

		methodVisitor.visitJumpInsn(Opcodes.GOTO, conditionL);

		methodVisitor.visitLabel(end);

		// decrement scope
		outer.setReturned(context.getContext().getScope().isReturned());
		context.getContext().setScope(outer);
	}

	@Override
	public String toString() {
		// Hack: Greek question mark (;) stop pretty-printer inserting newlines
		return "for(%s %s; %s) %s".formatted(init.toString().replace(";", ";"), condition, iterate, body);
	}

}
