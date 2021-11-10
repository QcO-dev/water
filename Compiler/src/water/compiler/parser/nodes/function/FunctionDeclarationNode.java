package water.compiler.parser.nodes.function;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.*;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.Pair;
import water.compiler.util.Unthrow;
import water.compiler.util.WaterType;

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
	private final List<Node> throwsList;
	private final Token access;
	private final Token staticModifier;
	private WaterType returnType;
	private String descriptor;

	public FunctionDeclarationNode(DeclarationType type, Token name, Node body, List<Pair<Token, Node>> parameters, Node returnType, List<Node> throwsList,
								   Token access, Token staticModifier) {
		this.type = type;
		this.name = name;
		this.body = body;
		this.parameters = parameters;
		this.returnTypeNode = returnType;
		this.throwsList = throwsList;
		this.access = access;
		this.staticModifier = staticModifier;
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		if(context.getType() == ContextType.GLOBAL) {
			preprocessGlobal(context);
		}
		// ContextType.CLASS
		else {
			preprocessClass(context);
		}

	}

	private void preprocessGlobal(Context context) throws SemanticException {
		try {
			if (context.getScope().exactLookupFunction(name.getValue(), parameters.stream().map(n -> Unthrow.wrap(() -> n.getSecond().getReturnType(context))).toArray(WaterType[]::new)) != null) throw new SemanticException(name,
					"Redefinition of function '%s' in global scope.".formatted(name.getValue()));

			computeReturnType(context, true);

			ArrayList<Function> functions = context.getScope().lookupFunctions(name.getValue());
			if(functions != null) {
				WaterType expectedReturnType = functions.get(0).getType().getReturnType();
				if (!expectedReturnType.equals(returnType)) {
					throw new SemanticException(name,
							"Function overloads may only differ in parameters, not return type. (%s =/= %s)"
									.formatted(returnType, expectedReturnType));
				}
			}

			makeDescriptor(context);
		} catch (ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		MethodVisitor mv = makeGlobalFunction(context);
		finalizeMethod(mv, context);

		context.getScope().addFunction(new Function(FunctionType.STATIC, name.getValue(), context.getCurrentClass(), WaterType.getMethodType(descriptor)));
	}

	private void preprocessClass(Context context) throws SemanticException {
		try {
			// Verify this function is unique
			Function function = context.getScope().exactLookupFunction(name.getValue(), parameters.stream().map(n -> Unthrow.wrap(() -> n.getSecond().getReturnType(context))).toArray(WaterType[]::new));

			if(function != null && function.getFunctionType() == FunctionType.CLASS) {
				throw new SemanticException(name, "Redefinition of function '%s' in current class.".formatted(name.getValue()));
			}

			computeReturnType(context, false);

			ArrayList<Function> functions = context.getScope().lookupFunctions(name.getValue());
			if(functions != null) {
				WaterType expectedReturnType = functions.get(0).getType().getReturnType();
				if (!expectedReturnType.equals(returnType)) {
					throw new SemanticException(name,
							"Function overloads may only differ in parameters, not return type. (%s =/= %s)"
									.formatted(returnType, expectedReturnType));
				}
			}

			makeDescriptor(context);
		} catch (ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		MethodVisitor mv = makeClassFunction(context);
		finalizeMethod(mv, context);

		context.getScope().addFunction(new Function(FunctionType.CLASS, name.getValue(), context.getCurrentClass(), WaterType.getMethodType(descriptor)));
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		context.getContext().setConstructor(false);
		MethodVisitor mv;
		if(context.getContext().getType() == ContextType.GLOBAL) mv = makeGlobalFunction(context.getContext());
		else mv = makeClassFunction(context.getContext());
		createMainMethod(context.getContext());
		mv.visitCode();

		addNullableAnnotations(mv, context.getContext());

		context.getContext().setMethodVisitor(mv);

		ContextType prev = context.getContext().getType();

		boolean isStatic = isStatic(context.getContext());

		context.getContext().setType(ContextType.FUNCTION);

		Scope outer = context.getContext().getScope();

		context.getContext().setScope(outer.nextDepth());

		context.getContext().getScope().setReturnType(returnType);


		context.getContext().setStaticMethod(isStatic);

		addParameters(context.getContext(), isStatic);

		body.visit(context);
		if(type == DeclarationType.EXPRESSION) mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));

		if(type == DeclarationType.STANDARD && !context.getContext().getScope().isReturned()) {
			if(returnType.equals(WaterType.VOID_TYPE))
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
		String[] exceptions = computeExceptions(context);
		return context.getCurrentClassWriter().visitMethod(access | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, name.getValue(), descriptor, null, exceptions);
	}

	private MethodVisitor makeClassFunction(Context context) throws SemanticException {
		int access = verifyAccess();
		int staticAccess = isStatic(context) ? Opcodes.ACC_STATIC : 0;
		String[] exceptions = computeExceptions(context);
		return context.getCurrentClassWriter().visitMethod(access | staticAccess, name.getValue(), descriptor, null, exceptions);
	}

	private void finalizeMethod(MethodVisitor mv, Context context) throws SemanticException {
		mv.visitCode();

		addNullableAnnotations(mv, context);

		if(!returnType.equals(WaterType.VOID_TYPE)) mv.visitInsn(returnType.dummyConstant());
		mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));

		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private void addNullableAnnotations(MethodVisitor visitor, Context context) throws SemanticException {
		if(returnType.isNullable() || returnType.needsDimensionAnnotation()) {
			AnnotationVisitor av = visitor.visitAnnotation("Lwater/runtime/annotation/Nullable;", true);
			returnType.writeAnnotationDimensions(av);
			av.visitEnd();
		}

		int nullableParameterCount = (int) parameters.stream().map(Pair::getSecond).map(n -> Unthrow.wrap(() -> n.getReturnType(context)))
				.filter(t -> t.isNullable() || t.needsDimensionAnnotation()).count();

		visitor.visitAnnotableParameterCount(nullableParameterCount, true);

		for(int i = 0; i < parameters.size(); i++) {
			WaterType parameterType = parameters.get(i).getSecond().getReturnType(context);
			if(parameterType.isNullable() || parameterType.needsDimensionAnnotation()) {
				AnnotationVisitor av = visitor.visitParameterAnnotation(i, "Lwater/runtime/annotation/Nullable;", true);
				parameterType.writeAnnotationDimensions(av);
				av.visitEnd();
			}
		}
	}

	private void makeDescriptor(Context context) {
		descriptor = "(%s)%s".formatted(parameters.stream().map(Pair::getSecond).map(n -> Unthrow.wrap(() -> n.getReturnType(context).getDescriptor())).collect(Collectors.joining()), returnType.getDescriptor());
	}

	private void computeReturnType(Context context, boolean isStatic) throws SemanticException {
		returnType = returnTypeNode == null ? WaterType.VOID_TYPE : returnTypeNode.getReturnType(context);

		if(type == DeclarationType.EXPRESSION) {
			// For the return type to be computed the parameter types may be needed, therefore a new Context is created.
			ContextType prev = context.getType();

			context.setType(ContextType.FUNCTION);

			Scope outer = context.getScope();

			context.setScope(outer.nextDepth());

			context.getScope().setReturnType(returnType);

			addParameters(context, isStatic);

			returnType = body.getReturnType(context);

			context.setScope(outer);

			context.setType(prev);
		}
	}

	private void addParameters(Context context, boolean isStatic) throws SemanticException {
		Scope scope = context.getScope();
		if(isStatic) {
			for (Pair<Token, Node> parameter : parameters) {

				WaterType parameterType = parameter.getSecond().getReturnType(context);
				scope.addVariable(new Variable(VariableType.LOCAL, parameter.getFirst().getValue(), scope.nextLocal(), parameterType, false));
				if(parameterType.getSize() == 2)
					scope.nextLocal();
			}
		}
		else {
			scope.nextLocal();
			for (Pair<Token, Node> parameter : parameters) {

				WaterType parameterType = parameter.getSecond().getReturnType(context);
				scope.addVariable(new Variable(VariableType.LOCAL, parameter.getFirst().getValue(), scope.nextLocal(), parameterType, false));

				if(parameterType.getSize() == 2)
					scope.nextLocal();
			}
		}
	}

	private void createMainMethod(Context context) throws SemanticException {
		if(!"main".equals(name.getValue()) // main
				|| parameters.size() != 0  // no args
				|| verifyAccess() != Opcodes.ACC_PUBLIC // public
				|| !isStatic(context) // static
				|| !returnType.equals(WaterType.VOID_TYPE)) // return void
			return;

		String[] exceptions = computeExceptions(context);
		MethodVisitor mainMethodWithArgs = context.getCurrentClassWriter().visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "main", "([Ljava/lang/String;)V", null, exceptions);

		mainMethodWithArgs.visitCode();
		mainMethodWithArgs.visitMethodInsn(Opcodes.INVOKESTATIC, context.getCurrentClass(), "main", "()V", false);
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

	private String[] computeExceptions(Context context) throws SemanticException {
		if(throwsList == null) return null;

		ArrayList<String> exceptions = new ArrayList<>();
		for(Node exception : throwsList) {
			WaterType exceptionType = exception.getReturnType(context);

			if(exceptionType.isPrimitive()) {
				throw new SemanticException(name, "Cannot throw primitive type (got '%s').".formatted(exceptionType));
			}

			try {
				if(!WaterType.getObjectType("java/lang/Throwable").isAssignableFrom(exceptionType, context, false)) {
					throw new SemanticException(name, "throw target must be an extension of java.lang.Throwable ('%s' cannot be cast).".formatted(exceptionType));
				}
			} catch (ClassNotFoundException e) {
				throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
			}
			exceptions.add(exceptionType.getInternalName());
		}
		return exceptions.toArray(String[]::new);
	}

	private boolean isStatic(Context context) {
		if(staticModifier != null) return true;
		if(context.getType() == ContextType.GLOBAL) return true;
		return false;
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
}
