package water.compiler.parser.nodes.value;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

public class NullNode implements Node {
	@Override
	public void visit(FileContext context) throws SemanticException {
		context.getContext().getMethodVisitor().visitInsn(Opcodes.ACONST_NULL);
	}

	@Override
	public WaterType getReturnType(Context context) throws SemanticException {
		return WaterType.OBJECT_TYPE.setNullable(true);
	}

	@Override
	public Object getConstantValue(Context context) {
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
