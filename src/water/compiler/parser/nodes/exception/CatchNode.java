package water.compiler.parser.nodes.exception;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.*;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

public class CatchNode implements Node {
	private final Token bindingName;
	private final Node exceptionType;
	private final Node body;

	private Label from;
	private Label to;
	private Label handler;

	private Node finallyBlock;
	private Label finallyLabel;
	private Label start;
	private Label end;

	public CatchNode(Token bindingName, Node exceptionType, Node body) {
		this.bindingName = bindingName;
		this.exceptionType = exceptionType;
		this.body = body;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Scope outer = context.getContext().getScope();

		context.getContext().setScope(outer.nextDepth());

		Type exception = getExceptionType(context.getContext());

		int varIndex = context.getContext().getScope().nextLocal();
		context.getContext().getScope().addVariable(new Variable(VariableType.LOCAL, bindingName.getValue(), varIndex, exception, true));

		context.getContext().getMethodVisitor().visitLabel(handler);
		context.getContext().getMethodVisitor().visitLabel(start);
		context.getContext().getMethodVisitor().visitVarInsn(Opcodes.ASTORE, varIndex);

		body.visit(context);
		context.getContext().getMethodVisitor().visitLabel(end);
		if(finallyBlock != null) finallyBlock.visit(context);

		context.getContext().setScope(outer);
	}

	public void updateMetadata(Label from, Label to, Node finallyBlock, Label finallyLabel) {
		this.from = from;
		this.to = to;
		this.finallyBlock = finallyBlock;
		this.finallyLabel = finallyLabel;
	}

	public void generateTryCatchBlock(MethodVisitor visitor, Context context) throws SemanticException {
		handler = new Label();
		start = new Label();
		end = new Label();
		visitor.visitTryCatchBlock(from, to, handler, getExceptionType(context).getInternalName());
		if(finallyBlock != null) {
			visitor.visitTryCatchBlock(start, end, finallyLabel, null);
		}
	}

	private Type getExceptionType(Context context) throws SemanticException {
		Type exception = exceptionType.getReturnType(context);

		if(TypeUtil.isPrimitive(exception)) {
			throw new SemanticException(bindingName, "Cannot throw primitive type (got '%s').".formatted(TypeUtil.stringify(exception)));
		}

		try {
			if(!TypeUtil.isAssignableFrom(Type.getObjectType("java/lang/Throwable"), exception, context, false)) {
				throw new SemanticException(bindingName, "throw target must be an extension of java.lang.Throwable ('%s' cannot be cast).".formatted(TypeUtil.stringify(exception)));
			}
		} catch (ClassNotFoundException | SemanticException e) {
			throw new SemanticException(bindingName, "Could not resolve class '%s'".formatted(e.getMessage()));
		}
		return exception;
	}

	@Override
	public String toString() {
		return "catch(%s: %s) %s".formatted(bindingName.getValue(), exceptionType, body);
	}
}
