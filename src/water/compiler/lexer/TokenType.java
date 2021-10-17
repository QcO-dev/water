package water.compiler.lexer;

/**
 * Defines valid types for tokens produced by {@link water.compiler.lexer.Lexer}
 */
public enum TokenType {
	//Single character
	LBRACE, RBRACE,
	LPAREN, RPAREN,
	LSQBR, RSQBR,
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

	// Inplace
	IN_PLUS,
	IN_MINUS,
	IN_MUL,
	IN_DIV,
	IN_MOD,

	// Identifiers / Keywords
	IDENTIFIER,
	IMPORT,
	PACKAGE,
	FUNCTION,
	VAR,
	CONST,
	CLASS,
	ENUM,
	CONSTRUCTOR,
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
	THIS,
	SUPER,
	VOID,
	INT,
	DOUBLE,
	BOOLEAN,
	CHAR,
	FLOAT,
	LONG,
	BYTE,
	SHORT,
	PUBLIC,
	PRIVATE,
	STATIC,
	THROW,
	THROWS,

	// Literals
	NUMBER,
	STRING,
	CHAR_LITERAL,

	// Special
	ERROR,
	EOF
}
