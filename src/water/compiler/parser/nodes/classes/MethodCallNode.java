package water.compiler.parser.nodes.classes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.variable.VariableAccessNode;
import water.compiler.util.TypeUtil;
import water.compiler.util.Unthrow;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodCallNode implements Node {

	private final Node left;
	private final Token name;
	private final List<Node> args;

	public MethodCallNode(Node left, Token name, List<Node> args) {
		this.left = left;
		this.name = name;
		this.args = args;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		if(left instanceof VariableAccessNode) ((VariableAccessNode) left).setMemberAccess(true);
		Type leftType = left.getReturnType(context.getContext());

		if(leftType.getSort() == Type.ARRAY && name.getValue().equals("length") && args.size() == 0) {
			left.visit(context);
			context.getContext().getMethodVisitor().visitInsn(Opcodes.ARRAYLENGTH);
			return;
		}

		if(leftType.getSort() != Type.OBJECT) {
			throw new SemanticException(name, "Cannot invoke method on type '%s'".formatted(TypeUtil.stringify(leftType)));
		}

		left.visit(context);

		Method toCall = resolve(leftType, context.getContext());

		for(Node n : args) n.visit(context);

		Stream<Type> paramTypes = Arrays.stream(toCall.getParameterTypes()).map(Type::getType);

		String descriptor = "(%s)%s"
				.formatted(paramTypes.map(Type::getDescriptor).collect(Collectors.joining()), Type.getType(toCall.getReturnType()).getDescriptor());

		context.getContext().getMethodVisitor().visitMethodInsn(TypeUtil.getInvokeOpcode(toCall), leftType.getInternalName(), name.getValue(), descriptor, false);

	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		if(left instanceof VariableAccessNode) ((VariableAccessNode) left).setMemberAccess(true);
		Type leftType = left.getReturnType(context);

		if(leftType.getSort() == Type.ARRAY && name.getValue().equals("length") && args.size() == 0) return Type.INT_TYPE;

		return Type.getType(resolve(leftType, context).getReturnType());
	}

	private Method resolve(Type leftType, Context context) throws SemanticException {
		Type[] argTypes = args.stream()
				.map(n -> Unthrow.wrap(() -> n.getReturnType(context))).toArray(Type[]::new);

		Class<?> klass;

		try {
			klass = Class.forName(leftType.getClassName(), false, context.getLoader());
		} catch (ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		Method toCall = null;

		try {
			out:
			for (Method method : klass.getMethods()) {
				if(!method.getName().equals(name.getValue())) continue;
				Type[] expectArgs = Type.getType(method).getArgumentTypes();

				if (expectArgs.length != argTypes.length) continue;

				for (int i = 0; i < expectArgs.length; i++) {
					Type expectArg = expectArgs[i];
					Type arg = argTypes[i];

					if (arg.getSort() == Type.VOID)
						continue out;

					if (!TypeUtil.typeToClass(expectArg, context)
							.isAssignableFrom(TypeUtil.typeToClass(arg, context)))
						continue out;
				}
				toCall = method;
			}
		}
		catch(ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}
		return toCall;
	}

	@Override
	public String toString() {
		return "%s.%s(%s)".formatted(left, name.getValue(), args.stream().map(Node::toString).collect(Collectors.joining(", ")));
	}
}
