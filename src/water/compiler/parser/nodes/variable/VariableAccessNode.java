package water.compiler.parser.nodes.variable;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.compiler.Variable;
import water.compiler.compiler.VariableType;
import water.compiler.lexer.Token;
import water.compiler.parser.LValue;
import water.compiler.parser.Node;

public class VariableAccessNode implements Node {
	private final Token name;

	public VariableAccessNode(Token name) {
		this.name = name;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		context.getContext().updateLine(name.getLine());
		Variable v = context.getContext().getScope().lookupVariable(name.getValue());

		if(v == null) throw new SemanticException(name, "Cannot resolve variable '%s' in current scope.".formatted(name.getValue()));

		if(v.getVariableType() == VariableType.GLOBAL) {
			context.getContext().getMethodVisitor().visitFieldInsn(Opcodes.GETSTATIC, v.getOwner(), v.getName(), v.getType().getDescriptor());
		}
		else {
			context.getContext().getMethodVisitor().visitVarInsn(v.getType().getOpcode(Opcodes.ILOAD), v.getIndex());
		}
	}

	@Override
	public Type getReturnType(Context context) throws SemanticException {
		Variable v = context.getScope().lookupVariable(name.getValue());

		if(v == null) throw new SemanticException(name, "Cannot resolve variable '%s' in current scope.".formatted(name.getValue()));

		return v.getType();
	}

	@Override
	public LValue getLValue() {
		return LValue.VARIABLE;
	}

	@Override
	public Object[] getLValueData() {
		return new Object[] { name };
	}

	@Override
	public String toString() {
		return name.getValue();
	}
}
