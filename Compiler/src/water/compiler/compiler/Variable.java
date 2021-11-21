package water.compiler.compiler;

import org.objectweb.asm.Type;
import water.compiler.util.WaterType;

/**
 * Represents a variable which can be accessed without class reference.
 */
public class Variable {
	private final String name;
	private final String owner;
	private WaterType type;
	private final VariableType variableType;
	private final int index;
	private final boolean isConst;

	public Variable(VariableType variableType, String name, String owner, WaterType type, boolean isConst) {
		this.name = name;
		this.owner = owner;
		this.type = type;
		this.variableType = variableType;
		this.isConst = isConst;
		this.index = 0;
	}

	public Variable(VariableType variableType, String name, int index, WaterType type, boolean isConst) {
		this.variableType = variableType;
		this.index = index;
		this.type = type;
		this.name = name;
		this.isConst = isConst;
		this.owner = null;
	}

	public String getName() {
		return name;
	}

	public String getOwner() {
		return owner;
	}

	public WaterType getType() {
		return type;
	}

	public VariableType getVariableType() {
		return variableType;
	}

	public int getIndex() {
		return index;
	}

	public boolean isConst() {
		return isConst;
	}

	public void setType(WaterType type) {
		this.type = type;
	}
}
