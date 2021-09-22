package water.compiler.parser.nodes.value;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

public class NumberNode implements Node {
	private final Token value;
	private Type type;

	public NumberNode(Token value) {
		this.value = value;
		type = computeCorrectType();
	}

	@Override
	public String toString() {
		return value.getValue();
	}

	@Override
	public void visit(FileContext context) {
		context.getContext().updateLine(value.getLine());
		if(type.getSort() == Type.INT) {
			TypeUtil.generateCorrectInt(Integer.parseInt(value.getValue()), context.getContext());
		}
		else if(type.getSort() == Type.DOUBLE) {
			TypeUtil.generateCorrectDouble(Double.parseDouble(value.getValue()), context.getContext());
		}
		else if(type.getSort() == Type.FLOAT) {
			TypeUtil.generateCorrectFloat(Float.parseFloat(value.getValue()), context.getContext());
		}
		else if(type.getSort() == Type.LONG) {
			TypeUtil.generateCorrectLong(Long.parseLong(value.getValue().substring(0, value.getValue().length() - 1)), context.getContext());
		}
	}

	@Override
	public Type getReturnType(Context context) {
		return computeCorrectType();
	}

	@Override
	public Object getConstantValue(Context context) {
		if(type.getSort() == Type.INT) return Integer.parseInt(value.getValue());
		else if(type.getSort() == Type.DOUBLE) return Double.parseDouble(value.getValue());
		else if(type.getSort() == Type.FLOAT) return Float.parseFloat(value.getValue());
		else if(type.getSort() == Type.LONG) return Long.parseLong(value.getValue().substring(0, value.getValue().length() - 1));

		return null; // Unreachable
	}

	@Override
	public boolean isConstant(Context context) {
		return true;
	}

	private Type computeCorrectType() {
		String rep = value.getValue();

		if(rep.endsWith("l") || rep.endsWith("L")) return Type.LONG_TYPE;
		if(rep.endsWith("f") || rep.endsWith("F")) return Type.FLOAT_TYPE;
		if(rep.contains(".")) return Type.DOUBLE_TYPE;
		return Type.INT_TYPE;
	}
}
