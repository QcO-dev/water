package water.compiler.compiler;

import org.objectweb.asm.Type;

/**
 * Represents a variable which can be accessed without class reference.
 */
public class Variable {
	private final String name;
	private final String owner;
	private final Type type;
	private final VariableType variableType;
	private final int index;

	public Variable(VariableType variableType, String name, String owner, Type type) {
		this.name = name;
		this.owner = owner;
		this.type = type;
		this.variableType = variableType;
		this.index = 0;
	}

	public Variable(VariableType variableType, String name, int index, Type type) {
		this.variableType = variableType;
		this.index = index;
		this.type = type;
		this.name = name;
		this.owner = null;
	}

	public String getName() {
		return name;
	}

	public String getOwner() {
		return owner;
	}

	public Type getType() {
		return type;
	}

	public VariableType getVariableType() {
		return variableType;
	}

	public int getIndex() {
		return index;
	}
}
