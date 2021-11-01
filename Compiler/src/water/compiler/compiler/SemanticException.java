package water.compiler.compiler;

import water.compiler.lexer.Token;

/**
 * Represents an error produced by the compiler, such as an unresolved variable
 */
public class SemanticException extends Exception {

	private final Token location;
	private final String message;

	public SemanticException(Token location, String message) {
		super(message);
		this.location = location;
		this.message = message;
	}

	public String getErrorMessage(String filename) {
		return "[%s:%s:%s] Semantic Error @ '%s': %s".formatted(filename, location.getLine(), location.getColumn(), location.getValue(), message);
	}
}
