package water.compiler.parser;

import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;

/**
 * Represents an error produced by the parser whilst processing tokens.
 */
public class UnexpectedTokenException extends Exception {
	private final Token token;
	private final String message;

	public UnexpectedTokenException(Token token, String message) {
		super(message);
		this.token = token;
		this.message = message;
	}

	public String getErrorMessage(String filename) {
		if(token.getType() == TokenType.EOF) return "[%s:%s:%s] Unexpected EOF: %s".formatted(filename, token.getLine(), token.getColumn(), message);

		return "[%s:%s:%s] Unexpected token @ '%s': %s".formatted(filename, token.getLine(), token.getColumn(), token.getValue(), message);
	}
}
