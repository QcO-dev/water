package water.compiler.parser.nodes.statement;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.SemanticException;
import water.compiler.parser.nodes.variable.AssignmentNode;
import water.compiler.util.OptimizationUtil;
import water.compiler.util.TypeUtil;
import water.compiler.parser.Node;
import water.compiler.util.WaterType;

public class ExpressionStatementNode implements Node {
	private final Node expression;

	public ExpressionStatementNode(Node expression) {
		this.expression = expression;
	}

	@Override
	public String toString() {
		return expression + ";";
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		if(expression.isConstant(context.getContext())) return;

		// Optimisation - removes dup_x1 and pop instructions for assignment
		boolean varAssign = OptimizationUtil.assignmentNodeExpressionEval(expression, context);

		WaterType returnType = expression.getReturnType(context.getContext());
		if(!returnType.equals(WaterType.VOID_TYPE) && !varAssign) context.getContext().getMethodVisitor().visitInsn(returnType.getPopOpcode());
	}
}
