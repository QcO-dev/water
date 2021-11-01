package water.compiler.parser.nodes.operation;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

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
		WaterType from = left.getReturnType(context);
		WaterType to = type.getReturnType(context);

		try {
			if(from.toClass(context).equals(to.toClass(context))) return;
		} catch (ClassNotFoundException e) {
			throw new SemanticException(as, "Could not resolve class '%s'".formatted(e.getMessage()));
		}

		if(from.equals(WaterType.VOID_TYPE)) throw new SemanticException(as, "Cannot cast from void");

		if(from.isPrimitive() && to.isPrimitive()) {
			left.visit(fc);
			from.cast(to, context.getMethodVisitor());
		}
		else if(!from.isPrimitive() && !to.isPrimitive()) {
			left.visit(fc);
			context.getMethodVisitor().visitTypeInsn(Opcodes.CHECKCAST, to.getInternalName());

			try {
				if(!to.toClass(context).isAssignableFrom(from.toClass(context))
				&& !from.toClass(context).isAssignableFrom(to.toClass(context)))
					throw new SemanticException(as, "Cannot cast type '%s' to '%s'".formatted(from.getClassName(), to.getClassName()));
			} catch (ClassNotFoundException e) {
				throw new SemanticException(as, "Could not resolve class '%s'".formatted(e.getMessage()));
			}
		}
		else {
			if(from.equals(WaterType.STRING_TYPE)) {
				stringCast(to, fc);
			}
			else throw new SemanticException(as, "Cannot cast between objects and primitives ('%s' to '%s')".formatted(from.getClassName(), to.getClassName()));
		}
	}

	private void stringCast(WaterType to, FileContext fc) throws SemanticException {
		//TODO other primitives
		switch (to.getSort()) {
			case INT -> {
				left.visit(fc);
				fc.getContext().getMethodVisitor().visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false);
			}
			case DOUBLE -> {
				left.visit(fc);
				fc.getContext().getMethodVisitor().visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "parseDouble", "(Ljava/lang/String;)D", false);
			}
			case BOOLEAN -> {
				left.visit(fc);
				fc.getContext().getMethodVisitor().visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "parseBoolean", "(Ljava/lang/String;)Z", false);
			}
			default -> throw new SemanticException(as, "Cannot cast between objects and primitives ('java.lang.String' to '%s')".formatted(to.getClassName()));
		}
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		return type.getReturnType(context);
	}

	@Override
	public String toString() {
		return left.toString() + " as " + type.toString();
	}
}
