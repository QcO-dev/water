package water.compiler.lexer;

/**
 * Stores information about a single token of a source.
 * Includes location, type, and a value (which often is a string representation of type)
 */
public class Token {
	private final int line;
	private final int column;
	private final TokenType type;
	private final String value;

	public Token(TokenType type, String value, int line, int column) {
		this.line = line;
		this.column = column;
		this.type = type;
		this.value = value;
	}

	public int getLine() {
		return line;
	}

	public int getColumn() {
		return column;
	}

	public TokenType getType() {
		return type;
	}

	public String getValue() {
		return value;
	}

	public String toString() {
		return "[%d:%d] %s: %s".formatted(line, column, type, value);
	}
}
