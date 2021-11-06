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
import water.compiler.util.WaterType;

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
	private boolean isSuper;

	public MethodCallNode(Node left, Token name, List<Node> args, boolean isSuper) {
		this.left = left;
		this.name = name;
		this.args = args;
		this.isStaticAccess = false;
		this.isSuper = isSuper;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		getLeftType(context.getContext()); // Initializes the variable access node to a static state (if in a static access)
		left.visit(context);

		WaterType returnType = left.getReturnType(context.getContext());
		if(returnType.isNullable()) {
			throw new SemanticException(name, "Cannot use '.' to call methods on a nullable type ('%s')".formatted(returnType));
		}

		visitCall(context);
	}

	public void visitCall(FileContext context) throws SemanticException {
		WaterType leftType = getLeftType(context.getContext());

		if(leftType.isArray() && name.getValue().equals("length") && args.size() == 0) {
			context.getContext().getMethodVisitor().visitInsn(Opcodes.ARRAYLENGTH);
			return;
		}

		if(!leftType.isObject()) {
			throw new SemanticException(name, "Cannot invoke method on type '%s'".formatted(leftType));
		}

		Method toCall = resolve(leftType, context.getContext());

		WaterType[] resolvedTypes = WaterType.getType(toCall).getArgumentTypes();

		for(int i = 0; i < args.size(); i++) {
			Node arg = args.get(i);
			WaterType resolvedType = resolvedTypes[i];

			arg.visit(context);
			try {
				resolvedType.isAssignableFrom(arg.getReturnType(context.getContext()), context.getContext(), true);
			} catch (ClassNotFoundException e) {
				throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
			}
		}

		Stream<WaterType> paramTypes = Arrays.stream(resolvedTypes);

		String descriptor = "(%s)%s"
				.formatted(paramTypes.map(WaterType::getDescriptor).collect(Collectors.joining()), Type.getType(toCall.getReturnType()).getDescriptor());

		context.getContext().getMethodVisitor().visitMethodInsn(isSuper ? Opcodes.INVOKESPECIAL : TypeUtil.getInvokeOpcode(toCall), leftType.getInternalName(), name.getValue(), descriptor, false);
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		WaterType leftType = getLeftType(context);

		if(leftType.isArray() && name.getValue().equals("length") && args.size() == 0) return WaterType.INT_TYPE;

		return WaterType.getType(resolve(leftType, context).getReturnType());
	}

	private WaterType getLeftType(Context context) throws SemanticException {
		if(left instanceof VariableAccessNode) {
			VariableAccessNode van = (VariableAccessNode) left;
			van.setMemberAccess(true);
			WaterType leftType = left.getReturnType(context);
			isStaticAccess = van.isStaticClassAccess();
			return leftType;
		}
		return left.getReturnType(context);
	}

	private Method resolve(WaterType leftType, Context context) throws SemanticException {
		WaterType[] argTypes = args.stream()
				.map(n -> Unthrow.wrap(() -> n.getReturnType(context))).toArray(WaterType[]::new);

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
				WaterType[] expectArgs = WaterType.getType(method).getArgumentTypes();

				if (expectArgs.length != argTypes.length) continue;

				int changes = 0;

				for (int i = 0; i < expectArgs.length; i++) {
					WaterType expectArg = expectArgs[i];
					WaterType arg = argTypes[i];

					if (arg.equals(WaterType.VOID_TYPE))
						continue out;

					if (expectArg.isAssignableFrom(arg, context, false)) {
						if (!expectArg.equals(arg)) changes += expectArg.assignChangesFrom(arg);
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
									List.of(argTypes).stream().map(WaterType::toString).collect(Collectors.joining(", "))));
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

		return appliedPossible.get(0).getSecond();
	}

	@Override
	public String toString() {
		return "%s.%s(%s)".formatted(left, name.getValue(), args.stream().map(Node::toString).collect(Collectors.joining(", ")));
	}
}
