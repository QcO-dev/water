package water.compiler.util;

import water.compiler.FileContext;
import water.compiler.compiler.SemanticException;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.variable.AssignmentNode;

public class OptimizationUtil {

	public static boolean assignmentNodeExpressionEval(Node expression, FileContext context) throws SemanticException {
		boolean varAssign = expression instanceof AssignmentNode;

		if(varAssign) ((AssignmentNode) expression).setExpressionStatementBody(true);

		expression.visit(context);

		return varAssign;
	}

}
