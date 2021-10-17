package water.compiler.parser.nodes.operation;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

public class CastNode implements Node {
	private final Node left;
	private final Node type;
	private final Token as;

	public CastNode(Node left, Node type, Token as) {
		this.left = left;
		this.type = type;
		this.as = as;
	}

	@Override
	public void visit(FileContext fc) throws SemanticException {
		Context context = fc.getContext();
		Type from = left.getReturnType(context);
		Type to = type.getReturnType(context);

		try {
			if(TypeUtil.typeToClass(from, context).equals(TypeUtil.typeToClass(to, context))) return;
		} catch (ClassNotFoundException e) {
			throw new SemanticException(as, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		if(from.getSort() == Type.VOID) throw new SemanticException(as, "Cannot cast from void");

		if(TypeUtil.isPrimitive(from) && TypeUtil.isPrimitive(to)) {
			left.visit(fc);
			TypeUtil.cast(context.getMethodVisitor(), from, to);
		}
		else if(!TypeUtil.isPrimitive(from) && !TypeUtil.isPrimitive(to)) {
			left.visit(fc);
			context.getMethodVisitor().visitTypeInsn(Opcodes.CHECKCAST, to.getInternalName());

			try {
				if(!TypeUtil.typeToClass(to, context).isAssignableFrom(TypeUtil.typeToClass(from, context))
				&& !TypeUtil.typeToClass(from, context).isAssignableFrom(TypeUtil.typeToClass(to, context)))
					throw new SemanticException(as, "Cannot cast type '%s' to '%s'".formatted(from.getClassName(), to.getClassName()));
			} catch (ClassNotFoundException e) {
				throw new SemanticException(as, "Could not resolve class '%s'".formatted(e.getMessage()));
			}
		}
		else {
			if(from.equals(TypeUtil.STRING_TYPE)) {
				stringCast(to, fc);
			}
			else throw new SemanticException(as, "Cannot cast between objects and primitives ('%s' to '%s')".formatted(from.getClassName(), to.getClassName()));
		}
	}

	private void stringCast(Type to, FileContext fc) throws SemanticException {
		//TODO other primitives
		switch (to.getSort()) {
			case Type.INT -> {
				left.visit(fc);
				fc.getContext().getMethodVisitor().visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
			}
			case Type.DOUBLE -> {
				left.visit(fc);
				fc.getContext().getMethodVisitor().visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
			}
			case Type.BOOLEAN -> {
				left.visit(fc);
				fc.getContext().getMethodVisitor().visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false);
			}
			default -> throw new SemanticException(as, "Cannot cast between objects and primitives ('java.lang.String' to '%s')".formatted(to.getClassName()));
		}
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return type.getReturnType(context);
	}

	@Override
	public String toString() {
		return left.toString() + " as " + type.toString();
	}
}
