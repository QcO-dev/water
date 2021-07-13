package water.compiler.parser.nodes.statement;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Scope;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

public class ReturnNode implements Node {
	private final Token returnTok;
	private final Node expression;

	public ReturnNode(Token returnTok, Node expression) {
		this.returnTok = returnTok;
		this.expression = expression;
	}


	@Override
	public void visit(FileContext context) throws SemanticException {
		context.getContext().updateLine(returnTok.getLine());
		MethodVisitor mv = context.getContext().getMethodVisitor();
		Scope scope = context.getContext().getScope();

		if(expression == null) {
			if(scope.getReturnType().getSort() != Type.VOID) throw new SemanticException(returnTok, "Non-void function's return must have a value.");
			mv.visitInsn(Opcodes.RETURN);
			scope.setReturned(true);
		}
		else {
			Type returnType = expression.getReturnType(context.getContext());

			if(returnType.getSort() == Type.VOID) throw new SemanticException(returnTok, "Cannot return void value");

			if(scope.getReturnType().getSort() == Type.VOID) throw new SemanticException(returnTok, "Cannot return value from void function");

			expression.visit(context);

			try {
				if(!TypeUtil.isAssignableFrom(scope.getReturnType(), returnType, context.getContext(), true)) {
					throw new SemanticException(returnTok,
							"Cannot return type '%s' from function expecting '%s'"
									.formatted(TypeUtil.stringify(returnType), TypeUtil.stringify(scope.getReturnType())));
				}
			} catch (ClassNotFoundException e) {
				throw new SemanticException(returnTok, "Could not resolve class '%s'".formatted(e.getMessage()));
			}

			mv.visitInsn(scope.getReturnType().getOpcode(Opcodes.IRETURN));
			scope.setReturned(true);
		}

	}

	@Override
	public String toString() {
		return "return" + (expression == null ? "" : (" " + expression.toString())) + ";";
	}
}
