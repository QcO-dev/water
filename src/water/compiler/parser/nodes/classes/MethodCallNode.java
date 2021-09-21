package water.compiler.parser.nodes.classes;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.variable.VariableAccessNode;
import water.compiler.util.Pair;
import water.compiler.util.TypeUtil;
import water.compiler.util.Unthrow;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodCallNode implements Node {

	private final Node left;
	private final Token name;
	private final List<Node> args;
	private boolean isStaticAccess;

	public MethodCallNode(Node left, Token name, List<Node> args) {
		this.left = left;
		this.name = name;
		this.args = args;
		this.isStaticAccess = false;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		Type leftType = getLeftType(context.getContext());

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

		Type[] resolvedTypes = Type.getType(toCall).getArgumentTypes();

		for(int i = 0; i < args.size(); i++) {
			Node arg = args.get(i);
			Type resolvedType = resolvedTypes[i];

			arg.visit(context);
			try {
				TypeUtil.isAssignableFrom(resolvedType, arg.getReturnType(context.getContext()), context.getContext(), true);
			} catch (ClassNotFoundException e) {
				throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
			}
		}

		Stream<Type> paramTypes = Arrays.stream(resolvedTypes);

		String descriptor = "(%s)%s"
				.formatted(paramTypes.map(Type::getDescriptor).collect(Collectors.joining()), Type.getType(toCall.getReturnType()).getDescriptor());

		context.getContext().getMethodVisitor().visitMethodInsn(TypeUtil.getInvokeOpcode(toCall), leftType.getInternalName(), name.getValue(), descriptor, false);

	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		Type leftType = getLeftType(context);

		if(leftType.getSort() == Type.ARRAY && name.getValue().equals("length") && args.size() == 0) return Type.INT_TYPE;

		return Type.getType(resolve(leftType, context).getReturnType());
	}

	private Type getLeftType(Context context) throws SemanticException {
		if(left instanceof VariableAccessNode) {
			VariableAccessNode van = (VariableAccessNode) left;
			van.setMemberAccess(true);
			Type leftType = left.getReturnType(context);
			isStaticAccess = van.isStaticClassAccess();
			return  leftType;
		}
		return left.getReturnType(context);
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

		ArrayList<Pair<Integer, Method>> possible = new ArrayList<>();

		try {
			out:
			for (Method method : klass.getMethods()) {
				if(!method.getName().equals(name.getValue())) continue;
				Type[] expectArgs = Type.getType(method).getArgumentTypes();

				if (expectArgs.length != argTypes.length) continue;

				int changes = 0;

				for (int i = 0; i < expectArgs.length; i++) {
					Type expectArg = expectArgs[i];
					Type arg = argTypes[i];

					if (arg.getSort() == Type.VOID)
						continue out;

					if (TypeUtil.isAssignableFrom(expectArg, arg, context, false)) {
						if (!expectArg.equals(arg)) changes++;
					} else {
						continue out;
					}
				}
				possible.add(new Pair<>(changes, method));
			}
		}
		catch(ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		if(possible.size() == 0) {
			throw new SemanticException(name,
					"Could not resolve method '%s' with arguments: %s".formatted(name.getValue(),
							argTypes.length == 0 ? "(none)" :
									List.of(argTypes).stream().map(TypeUtil::stringify).collect(Collectors.joining(", "))));
		}

		List<Pair<Integer, Method>> appliedPossible = possible.stream()
				.filter(p -> Modifier.isStatic(p.getSecond().getModifiers()) == isStaticAccess)
				.sorted(Comparator.comparingInt(Pair::getFirst)).toList();

		if(appliedPossible.size() == 0) {
			if(isStaticAccess)
				throw new SemanticException(name, "Cannot invoke non-static method from static class.");
			else
				throw new SemanticException(name, "Cannot invoke static method from non-static object.");
		}

		return possible.get(0).getSecond();
	}

	@Override
	public String toString() {
		return "%s.%s(%s)".formatted(left, name.getValue(), args.stream().map(Node::toString).collect(Collectors.joining(", ")));
	}
}
