package water.compiler.compiler;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Provides information about a function available without class reference
 */
public class Function {
	private final FunctionType functionType;
	private final String name;
	private final String owner;
	private final Type type;

	public Function(FunctionType functionType, String name, String owner, Type type) {
		this.functionType = functionType;
		this.name = name;
		this.type = type;
		this.owner = owner;
	}

	public FunctionType getFunctionType() {
		return functionType;
	}

	public String getName() {
		return name;
	}

	public Type getType() {
		return type;
	}

	public String getOwner() {
		return owner;
	}

	/**
	 * Gets the correct opcode for this function call
	 * @return The opcode to call this function
	 */
	public int getAccess() {
		return switch (functionType) {
			case STATIC -> Opcodes.INVOKESTATIC;
			case CLASS, SOUT -> Opcodes.INVOKEVIRTUAL;
		};
	}
}
