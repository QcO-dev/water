package water.compiler.parser.nodes.function;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.*;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.Pair;
import water.compiler.util.TypeUtil;
import water.compiler.util.Unthrow;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionDeclarationNode implements Node {

	public enum DeclarationType {
		STANDARD,
		EXPRESSION
	}

	private final DeclarationType type;
	private final Token name;
	private final Node body;
	private final List<Pair<Token, Node>> parameters;
	private final Node returnTypeNode;
	private final Token access;
	private Type returnType;
	private String descriptor;

	public FunctionDeclarationNode(DeclarationType type, Token name, Node body, List<Pair<Token, Node>> parameters, Node returnType, Token access) {
		this.type = type;
		this.name = name;
		this.body = body;
		this.parameters = parameters;
		this.returnTypeNode = returnType;
		this.access = access;
	}

	@Override
	public String toString() {
		if(type == DeclarationType.EXPRESSION) {
			return "function %s(%s) = %s;".formatted(name.getValue(),
					parameters.stream().map(p -> p.getFirst().getValue() + ": " + p.getSecond()).collect(Collectors.joining(", ")),
					body);
		}

		return "function %s(%s)%s %s".formatted(name.getValue(),
				parameters.stream().map(p -> p.getFirst().getValue() + ": " + p.getSecond()).collect(Collectors.joining(", ")),
				returnTypeNode == null ? "" : " -> " + returnTypeNode,
				body);
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		//TODO non-global

		try {
			if (context.getScope().lookupFunction(name.getValue(), parameters.stream().map(n -> Unthrow.wrap(() -> n.getSecond().getReturnType(context))).toArray(Type[]::new)) != null) throw new SemanticException(name,
					"Redefinition of function '%s' in global scope.".formatted(name.getValue()));

			returnType = returnTypeNode == null ? Type.VOID_TYPE : returnTypeNode.getReturnType(context);

			if(type == DeclarationType.EXPRESSION) {
				// For the return type to be computed the parameter types may be needed, therefore a new Context is created.
				ContextType prev = context.getType();

				context.setType(ContextType.FUNCTION);

				Scope outer = context.getScope();

				context.setScope(outer.nextDepth());

				context.getScope().setLocalIndex(parameters.size());

				context.getScope().setReturnType(returnType);

				for (int i = 0; i < parameters.size(); i++) {
					Pair<Token, Node> parameter = parameters.get(i);

					context.getScope().addVariable(new Variable(VariableType.LOCAL, parameter.getFirst().getValue(), i, parameter.getSecond().getReturnType(context)));
				}

				returnType = body.getReturnType(context);

				context.setScope(outer);

				context.setType(prev);
			}

			ArrayList<Function> functions = context.getScope().lookupFunctions(name.getValue());
			if(functions != null) {
				Type expectedReturnType = functions.get(0).getType().getReturnType();
				if (!expectedReturnType.equals(returnType)) {
					throw new SemanticException(name,
							"Function overloads may only differ in parameters, not return type. (%s =/= %s)"
									.formatted(TypeUtil.stringify(returnType), TypeUtil.stringify(expectedReturnType)));
				}
			}

			descriptor = "(%s)%s".formatted(parameters.stream().map(Pair::getSecond).map(n -> Unthrow.wrap(() -> n.getReturnType(context).getDescriptor())).collect(Collectors.joining()), returnType.getDescriptor());
		} catch (ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		MethodVisitor mv = makeGlobalFunction(context);
		mv.visitCode();

		if(returnType.getSort() != Type.VOID) mv.visitInsn(TypeUtil.dummyConstant(returnType));
		mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));

		mv.visitMaxs(0, 0);
		mv.visitEnd();

		context.getScope().addFunction(new Function(FunctionType.STATIC, name.getValue(), "", Type.getMethodType(descriptor)));
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		//TODO non-global
		MethodVisitor mv = makeGlobalFunction(context.getContext());
		if(context.getContext().getType() == ContextType.GLOBAL) createMainMethod(context.getContext());
		mv.visitCode();

		context.getContext().setMethodVisitor(mv);

		ContextType prev = context.getContext().getType();

		context.getContext().setType(ContextType.FUNCTION);

		Scope outer = context.getContext().getScope();

		context.getContext().setScope(outer.nextDepth());

		context.getContext().getScope().setLocalIndex(parameters.size());

		context.getContext().getScope().setReturnType(returnType);

		for (int i = 0; i < parameters.size(); i++) {
			Pair<Token, Node> parameter = parameters.get(i);

			context.getContext().getScope().addVariable(new Variable(VariableType.LOCAL, parameter.getFirst().getValue(), i, parameter.getSecond().getReturnType(context.getContext())));
		}

		body.visit(context);
		if(type == DeclarationType.EXPRESSION) mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));

		if(type == DeclarationType.STANDARD && !context.getContext().getScope().isReturned()) {
			if(returnType.getSort() == Type.VOID)
				mv.visitInsn(Opcodes.RETURN);
			else
				throw new SemanticException(name, "Non-void function must return a value.");
		}

		context.getContext().setScope(outer);

		context.getContext().setType(prev);

		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private MethodVisitor makeGlobalFunction(Context context) throws SemanticException {
		int access = verifyAccess();
		return context.getClassWriter().visitMethod(access | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, name.getValue(), descriptor, null, null);
	}

	private void createMainMethod(Context context) throws SemanticException {
		//TODO
		if(!"main".equals(name.getValue()) // main
				|| parameters.size() != 0  // no args
				|| verifyAccess() != Opcodes.ACC_PUBLIC // public
				|| returnType.getSort() != Type.VOID) // return void
			return;

		MethodVisitor mainMethodWithArgs = context.getClassWriter().visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "main", "([Ljava/lang/String;)V", null, null);

		mainMethodWithArgs.visitCode();
		mainMethodWithArgs.visitMethodInsn(Opcodes.INVOKESTATIC, context.getClassName(), "main", "()V", false);
		mainMethodWithArgs.visitInsn(Opcodes.RETURN);
		mainMethodWithArgs.visitMaxs(0, 0);
		mainMethodWithArgs.visitEnd();
	}

	private int verifyAccess() throws SemanticException {
		if(access == null) return Opcodes.ACC_PUBLIC;
		return switch (access.getType()) {
			case PUBLIC -> Opcodes.ACC_PUBLIC;
			case PRIVATE -> Opcodes.ACC_PRIVATE;
			default -> throw new SemanticException(access, "Invalid access modifier for function '%s'".formatted(access.getValue()));
		};
	}
}
