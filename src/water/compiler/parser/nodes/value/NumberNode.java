package water.compiler.parser.nodes.value;

import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.lexer.Token;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;
import water.compiler.util.WaterType;

public class NumberNode implements Node {
	private final Token value;
	private WaterType type;

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
		if(type.equals(WaterType.INT_TYPE)) {
			TypeUtil.generateCorrectInt(Integer.parseInt(value.getValue()), context.getContext());
		}
		else if(type.equals(WaterType.DOUBLE_TYPE)) {
			TypeUtil.generateCorrectDouble(Double.parseDouble(value.getValue()), context.getContext());
		}
		else if(type.equals(WaterType.FLOAT_TYPE)) {
			TypeUtil.generateCorrectFloat(Float.parseFloat(value.getValue()), context.getContext());
		}
		else if(type.equals(WaterType.LONG_TYPE)) {
			TypeUtil.generateCorrectLong(Long.parseLong(value.getValue().substring(0, value.getValue().length() - 1)), context.getContext());
		}
	}

	@Override
	public WaterType getReturnType(Context context) {
		return computeCorrectType();
	}

	@Override
	public Object getConstantValue(Context context) {
		if(type.equals(WaterType.INT_TYPE)) return Integer.parseInt(value.getValue());
		else if(type.equals(WaterType.DOUBLE_TYPE)) return Double.parseDouble(value.getValue());
		else if(type.equals(WaterType.FLOAT_TYPE)) return Float.parseFloat(value.getValue());
		else if(type.equals(WaterType.LONG_TYPE)) return Long.parseLong(value.getValue().substring(0, value.getValue().length() - 1));

		return null; // Unreachable
	}

	@Override
	public boolean isConstant(Context context) {
		return true;
	}

	private WaterType computeCorrectType() {
		String rep = value.getValue();

		if(rep.endsWith("l") || rep.endsWith("L")) return WaterType.LONG_TYPE;
		if(rep.endsWith("f") || rep.endsWith("F")) return WaterType.FLOAT_TYPE;
		if(rep.contains(".")) return WaterType.DOUBLE_TYPE;
		return WaterType.INT_TYPE;
	}
}
