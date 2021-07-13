package water.compiler.lexer;

/**
 * Defines valid types for tokens produced by {@link water.compiler.lexer.Lexer}
 */
public enum TokenType {
	//Single character
	LBRACE, RBRACE,
	LPAREN, RPAREN,
	SEMI, COLON,
	COMMA, DOT,
	PLUS, MINUS,
	STAR, SLASH, PERCENT,
	EQUALS,
	EXCLAIM,
	LESS, GREATER,

	// Multi character
	ARROW,
	EQEQ, TRI_EQ,
	EXEQ, TRI_EXEQ,
	LESS_EQ, GREATER_EQ,

	// Identifiers / Keywords
	IDENTIFIER,
	IMPORT,
	FUNCTION,
	VAR,
	AS,
	RETURN,
	FOR,
	WHILE,
	IF,
	ELSE,
	NEW,
	TRUE,
	FALSE,
	NULL,
	VOID,
	INT,
	DOUBLE,
	BOOLEAN,

	// Literals
	NUMBER,
	STRING,

	// Special
	ERROR,
	EOF
}
