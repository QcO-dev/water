package water.compiler.parser.nodes.classes;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;
import water.compiler.util.Unthrow;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ObjectConstructorNode implements Node {

	private final Token newToken;
	private final Node type;
	private final List<Node> arguments;

	public ObjectConstructorNode(Token newToken, Node type, List<Node> arguments) {
		this.newToken = newToken;
		this.type = type;
		this.arguments = arguments;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Type objType = type.getReturnType(context.getContext());

		if(TypeUtil.isPrimitive(objType)) {
			throw new SemanticException(newToken, "Cannot create new instance of primitive type (%s)"
				.formatted(TypeUtil.stringify(objType)));
		}

		context.getContext().updateLine(newToken.getLine());

		Type[] argTypes = arguments.stream()
				.map(n -> Unthrow.wrap(() -> n.getReturnType(context.getContext()))).toArray(Type[]::new);

		Class<?> klass;

		try {
			klass = Class.forName(objType.getClassName(), false, context.getContext().getLoader());
		} catch (ClassNotFoundException e) {
			throw new SemanticException(newToken, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		Constructor<?> toCall = getConstructor(klass.getConstructors(), argTypes, context);

		MethodVisitor methodVisitor = context.getContext().getMethodVisitor();
		methodVisitor.visitTypeInsn(Opcodes.NEW, objType.getInternalName());
		methodVisitor.visitInsn(Opcodes.DUP);

		for(Node n : arguments) n.visit(context);

		String descriptor;

		if(toCall == null) {
			Constructor<?> declaredConstructor = getConstructor(klass.getDeclaredConstructors(), argTypes, context);
			if(declaredConstructor == null) throw new SemanticException(newToken, "Class '%s' cannot be instantiated with arguments: %s"
					.formatted(TypeUtil.stringify(objType), Arrays.stream(argTypes).map(TypeUtil::stringify).collect(Collectors.joining(", "))));

			int modifiers = declaredConstructor.getModifiers();
			if(Modifier.isPrivate(modifiers) || Modifier.isProtected(modifiers)) {
				throw new SemanticException(newToken, "Class '%s' does not have a public constructor".formatted(TypeUtil.stringify(objType)));
			}

			descriptor = "(%s)V".formatted(
					Arrays.stream(declaredConstructor.getParameterTypes()).map(Type::getType).map(Type::getDescriptor).collect(Collectors.joining()));
		}
		else {
			descriptor = "(%s)V".formatted(
					Arrays.stream(toCall.getParameterTypes()).map(Type::getType).map(Type::getDescriptor).collect(Collectors.joining()));
		}

		methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL,
				objType.getInternalName(), "<init>", descriptor, false);
	}

	private Constructor<?> getConstructor(Constructor<?>[] constructors, Type[] argTypes, FileContext context) throws SemanticException {
		Constructor<?> toCall = null;

		try {
			out:
			for (Constructor<?> c : constructors) {
				Type[] expectArgs = Type.getType(c).getArgumentTypes();

				if (expectArgs.length != argTypes.length) continue;

				for (int i = 0; i < expectArgs.length; i++) {
					Type expectArg = expectArgs[i];
					Type arg = argTypes[i];

					if (arg.getSort() == Type.VOID)
						continue out;

					if (!TypeUtil.typeToClass(expectArg, context.getContext())
							.isAssignableFrom(TypeUtil.typeToClass(arg, context.getContext())))
						continue out;
				}
				toCall = c;
			}
		}
		catch(ClassNotFoundException e) {
			throw new SemanticException(newToken, "Could not resolve class '%s'".formatted(e.getMessage()));
		}
		return toCall;
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return type.getReturnType(context);
	}

	@Override
	public String toString() {
		return "new %s(%s)".formatted(type, arguments.stream().map(Node::toString).collect(Collectors.joining(", ")));
	}
}
