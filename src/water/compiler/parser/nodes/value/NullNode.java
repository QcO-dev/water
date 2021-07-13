package water.compiler.parser.nodes.value;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.parser.Node;

public class NullNode implements Node {
	@Override
	public void visit(FileContext context) throws SemanticException {
		context.getContext().getMethodVisitor().visitInsn(Opcodes.ACONST_NULL);
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		return Type.getObjectType("java/lang/Object");
	}

	@Override
	public Object getConstantValue(Context context) throws SemanticException {
		return null;
	}

	@Override
	public boolean isConstant(Context context) throws SemanticException {
		return true;
	}

	@Override
	public String toString() {
		return "null";
	}
}
