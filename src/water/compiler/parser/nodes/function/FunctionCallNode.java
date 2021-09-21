package water.compiler.parser.nodes.function;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.Function;
import water.compiler.compiler.FunctionType;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

import java.util.List;
import java.util.stream.Collectors;

public class FunctionCallNode implements Node {

	private final Token name;
	private final List<Node> args;

	public FunctionCallNode(Token name, List<Node> args) {
		this.name = name;
		this.args = args;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		context.getContext().updateLine(name.getLine());
		Type[] argTypes = new Type[args.size()];

		for(int i = 0; i < args.size(); i++) {
			Node arg = args.get(i);

			argTypes[i] = arg.getReturnType(context.getContext());
		}

		try {
			Function function = context.getContext().getScope().lookupFunction(name.getValue(), argTypes);

			if(function == null) throw new SemanticException(name,
					"Could not resolve function '%s' with arguments: %s".formatted(name.getValue(),
							argTypes.length == 0 ? "(none)" :
							List.of(argTypes).stream().map(TypeUtil::stringify).collect(Collectors.joining(", "))));

			if(function.getFunctionType() == FunctionType.SOUT)
				context.getContext().getMethodVisitor().visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
			else if(function.getFunctionType() == FunctionType.CLASS) {
				if(context.getContext().isStaticMethod()) throw new SemanticException(name, "Cannot invoke instance method '%s' from static context.".formatted(name.getValue()));
				context.getContext().getMethodVisitor().visitVarInsn(Opcodes.ALOAD, 0);
			}

			// Build args with lookupFunction
			context.getContext().getScope().lookupFunction(name.getValue(), argTypes, args.toArray(Node[]::new), true, context);

			context.getContext().getMethodVisitor().visitMethodInsn(function.getAccess(), function.getOwner(), function.getName(), function.getType().getDescriptor(), false);

		} catch (ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		Type[] argTypes = new Type[args.size()];

		for(int i = 0; i < args.size(); i++) {
			Node arg = args.get(i);

			argTypes[i] = arg.getReturnType(context);
		}

		try {
			Function function = context.getScope().lookupFunction(name.getValue(), argTypes);

			if(function == null) throw new SemanticException(name,
					"Could not resolve function '%s' with arguments: %s".formatted(name.getValue(),
							argTypes.length == 0 ? "(none)" :
									List.of(argTypes).stream().map(TypeUtil::stringify).collect(Collectors.joining(", "))));

			return function.getType().getReturnType();

		} catch (ClassNotFoundException e) {
			throw new SemanticException(name, "Could not resolve class '%s'".formatted(e.getMessage()));
		}
	}

	@Override
	public String toString() {
		return name.getValue() + "(" + args.stream().map(Node::toString).collect(Collectors.joining(", ")) + ")";
	}
}
