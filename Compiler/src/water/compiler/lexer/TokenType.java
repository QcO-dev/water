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
	BITWISE_AND,
	BITWISE_OR,
	BITWISE_XOR,
	BITWISE_NOT,
	QUESTION,

	// Multi character
	ARROW,
	EQEQ, TRI_EQ,
	EXEQ, TRI_EXEQ,
	LESS_EQ, GREATER_EQ,
	LOGICAL_AND,
	LOGICAL_OR,
	BITWISE_SHL,
	BITWISE_SHR,
	BITWISE_USHR,
	QUESTION_DOT,

	// Inplace
	IN_PLUS,
	IN_MINUS,
	IN_MUL,
	IN_DIV,
	IN_MOD,
	IN_BITWISE_SHL,
	IN_BITWISE_SHR,
	IN_BITWISE_USHR,
	IN_BITWISE_AND,
	IN_BITWISE_OR,
	IN_BITWISE_XOR,

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
	TRY,
	CATCH,
	FINALLY,

	// Literals
	NUMBER,
	STRING,
	CHAR_LITERAL,

	// Special
	ERROR,
	EOF
}
