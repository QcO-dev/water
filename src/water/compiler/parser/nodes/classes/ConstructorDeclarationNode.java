package water.compiler.parser.nodes.classes;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.*;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.Node;
import water.compiler.util.Pair;
import water.compiler.util.Unthrow;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConstructorDeclarationNode implements Node {
	private final Token access;
	private final List<Pair<Token, Node>> parameters;
	private final Node body;
	private final List<Node> variablesInit;

	public ConstructorDeclarationNode(Token access, List<Pair<Token, Node>> parameters, Node body) {
		this.access = access;
		this.parameters = parameters;
		this.body = body;
		this.variablesInit = new ArrayList<>();
	}

	@Override
	public void visit(FileContext fc) throws SemanticException {
		Context context = fc.getContext();
		MethodVisitor constructor = createConstructor(context);
		context.setDefaultConstructor(constructor);
		context.setMethodVisitor(constructor);

		for(Node variable : variablesInit) {
			variable.visit(fc);
			context.setMethodVisitor(constructor);
		}

		ContextType prev = context.getType();
		context.setType(ContextType.FUNCTION);

		Scope outer = context.getScope();

		context.setScope(outer.nextDepth());

		context.getScope().setLocalIndex(1 + parameters.size());

		for (int i = 0; i < parameters.size(); i++) {
			Pair<Token, Node> parameter = parameters.get(i);

			context.getScope().addVariable(new Variable(VariableType.LOCAL, parameter.getFirst().getValue(), i + 1, parameter.getSecond().getReturnType(context), false));
		}

		body.visit(fc);

		context.setScope(outer);

		context.setType(prev);

		constructor.visitInsn(Opcodes.RETURN);
		constructor.visitMaxs(0, 0);
		constructor.visitEnd();
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		MethodVisitor constructor = createConstructor(context);
		constructor.visitInsn(Opcodes.RETURN);
		constructor.visitMaxs(0, 0);
		constructor.visitEnd();
	}

	private MethodVisitor createConstructor(Context context) {
		ClassWriter writer = context.getCurrentClassWriter();
		String args = parameters.stream().map(Pair::getSecond).map(n -> Unthrow.wrap(() -> n.getReturnType(context).getDescriptor())).collect(Collectors.joining());
		MethodVisitor constructor = writer.visitMethod(getAccess(), "<init>", "(" + args + ")V", null, null);
		constructor.visitCode();

		constructor.visitVarInsn(Opcodes.ALOAD, 0);
		constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

		return constructor;
	}

	private int getAccess() {
		if(access == null || access.getType() == TokenType.PUBLIC) return Opcodes.ACC_PUBLIC;
		return Opcodes.ACC_PRIVATE;
	}

	public void addVariable(Node variable) {
		variablesInit.add(variable);
	}

	@Override
	public String toString() {
		return "constructor(%s) %s".formatted(
				parameters.stream().map(p -> p.getFirst().getValue() + ": " + p.getSecond()).collect(Collectors.joining(", ")),
				body
		);
	}
}
