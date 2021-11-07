package water.compiler.parser.nodes.classes;

import org.objectweb.asm.*;
import water.compiler.FileContext;
import water.compiler.compiler.*;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.variable.VariableDeclarationNode;
import water.compiler.util.Pair;
import water.compiler.util.TypeUtil;
import water.compiler.util.Unthrow;
import water.compiler.util.WaterType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ConstructorDeclarationNode implements Node {
	private final Token access;
	private final Token constructorToken;
	private final List<Node> superArgs;
	private final List<Pair<Token, Node>> parameters;
	private final Node body;
	private List<VariableDeclarationNode> variablesInit;

	public ConstructorDeclarationNode(Token access, Token constructor, List<Node> superArgs, List<Pair<Token, Node>> parameters, Node body) {
		this.access = access;
		this.constructorToken = constructor;
		this.superArgs = superArgs;
		this.parameters = parameters;
		this.body = body;
		this.variablesInit = new ArrayList<>();
	}

	@Override
	public void visit(FileContext fc) throws SemanticException {
		Context context = fc.getContext();

		ClassWriter writer = context.getCurrentClassWriter();
		String args = parameters.stream().map(Pair::getSecond).map(n -> Unthrow.wrap(() -> n.getReturnType(context).getDescriptor())).collect(Collectors.joining());
		MethodVisitor constructor = writer.visitMethod(getAccess(), "<init>", "(" + args + ")V", null, null);
		constructor.visitCode();

		addNullableAnnotations(constructor, fc.getContext());

		context.setDefaultConstructor(constructor);
		context.setMethodVisitor(constructor);
		context.setConstructor(true);

		constructor.visitVarInsn(Opcodes.ALOAD, 0);

		Scope outer = context.getScope();

		Scope inner = outer.nextDepth();

		context.setScope(inner);

		context.getScope().setLocalIndex(1 + parameters.size());

		for (int i = 0; i < parameters.size(); i++) {
			Pair<Token, Node> parameter = parameters.get(i);

			context.getScope().addVariable(new Variable(VariableType.LOCAL, parameter.getFirst().getValue(), i + 1, parameter.getSecond().getReturnType(context), false));
		}

		createSuperCall(constructor, fc);

		// Variables should not be in current constructor scope.
		context.setScope(outer);

		for(Node variable : variablesInit) {
			variable.visit(fc);
			context.setMethodVisitor(constructor);
		}

		ContextType prev = context.getType();
		context.setType(ContextType.FUNCTION);
		// Back into the constructor scope.
		context.setScope(inner);

		body.visit(fc);

		context.setScope(outer);

		context.setType(prev);

		constructor.visitInsn(Opcodes.RETURN);
		constructor.visitMaxs(0, 0);
		constructor.visitEnd();
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		MethodVisitor constructor = createDefaultConstructor(context);
		constructor.visitInsn(Opcodes.RETURN);
		constructor.visitMaxs(0, 0);
		constructor.visitEnd();
	}

	private void addNullableAnnotations(MethodVisitor visitor, Context context) throws SemanticException {
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

	private MethodVisitor createDefaultConstructor(Context context) throws SemanticException {
		ClassWriter writer = context.getCurrentClassWriter();
		String args = parameters.stream().map(Pair::getSecond).map(n -> Unthrow.wrap(() -> n.getReturnType(context).getDescriptor())).collect(Collectors.joining());
		MethodVisitor constructor = writer.visitMethod(getAccess(), "<init>", "(" + args + ")V", null, null);
		constructor.visitCode();

		addNullableAnnotations(constructor, context);

		constructor.visitVarInsn(Opcodes.ALOAD, 0);
		constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, context.getCurrentSuperClass().getInternalName(), "<init>", "()V", false);

		return constructor;
	}

	private void createSuperCall(MethodVisitor methodVisitor, FileContext fc) throws SemanticException {
		Context context = fc.getContext();
		WaterType[] argTypes;
		if(superArgs == null) {
			argTypes = new WaterType[] {};
		}
		else {
			argTypes = superArgs.stream().map(n -> Unthrow.wrap(() -> n.getReturnType(context))).toArray(WaterType[]::new);
		}

		Class<?> klass;

		try {
			klass = Class.forName(context.getCurrentSuperClass().getClassName(), false, context.getLoader());
		} catch (ClassNotFoundException e) {
			throw new SemanticException(constructorToken, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		Constructor<?>[] constructors = Arrays.stream(klass.getDeclaredConstructors()).filter(c -> !Modifier.isPrivate(c.getModifiers())).toArray(Constructor[]::new);

		Constructor<?> superConstructor = TypeUtil.getConstructor(constructorToken, constructors, argTypes, context);

		if(superConstructor == null) throw new SemanticException(constructorToken, "SuperClass '%s' cannot be instantiated with arguments: %s"
				.formatted(context.getCurrentSuperClass(),
						Arrays.stream(argTypes).map(WaterType::toString).collect(Collectors.joining(", "))));

		WaterType[] resolvedTypes = WaterType.getType(superConstructor).getArgumentTypes();
		if(superArgs != null) {
			for (int i = 0; i < superArgs.size(); i++) {
				Node arg = superArgs.get(i);
				WaterType resolvedType = resolvedTypes[i];

				arg.visit(fc);
				try {
					resolvedType.isAssignableFrom(arg.getReturnType(context), context, true);
				} catch (ClassNotFoundException e) {
					throw new SemanticException(constructorToken, "Could not resolve class '%s'".formatted(e.getMessage()));
				}
			}
		}

		String descriptor = "(%s)V".formatted(Arrays.stream(
				superConstructor.getParameterTypes()).map(Type::getType).map(Type::getDescriptor).collect(Collectors.joining()));

		methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, context.getCurrentSuperClass().getInternalName(), "<init>", descriptor, false);
	}

	private int getAccess() {
		if(access == null || access.getType() == TokenType.PUBLIC) return Opcodes.ACC_PUBLIC;
		return Opcodes.ACC_PRIVATE;
	}

	public void setVariablesInit(List<VariableDeclarationNode> variablesInit) {
		this.variablesInit = variablesInit;
	}

	@Override
	public String toString() {
		return "constructor(%s) %s".formatted(
				parameters.stream().map(p -> p.getFirst().getValue() + ": " + p.getSecond()).collect(Collectors.joining(", ")),
				body
		);
	}
}
