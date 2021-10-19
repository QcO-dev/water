package water.compiler.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * Given source code, produces ordered tokens.
 * These tokens can then be processed by {@link water.compiler.parser.Parser}
 */
public class Lexer {

	/** TokenTypes which represent keywords for primitives, e.g. 'int' */
	public static List<TokenType> PRIMITIVE_TYPES = List.of(TokenType.INT, TokenType.DOUBLE,
			TokenType.FLOAT, TokenType.BOOLEAN, TokenType.CHAR, TokenType.LONG, TokenType.BYTE, TokenType.SHORT);

	private int index;
	private int line;
	private int column;
	private int start;
	private char current;
	private String text;

	/**
	 * Takes in a source string, and produces a list of tokens.
	 *
	 *
	 * @param text The source of a water program
	 * @return A list of tokens
	 */
	public List<Token> lex(String text) {
		this.text = text;

		this.start = 0;
		this.index = 0;
		this.line = 1;
		this.column = 0;
		this.current = text.charAt(0);

		ArrayList<Token> tokens = new ArrayList<>();

		while(!isAtEnd()) {
			start = index;

			advance();

			Token token;

			if(isWhitespace(current)) {
				if (current == '\n') {
					line++;
					column = 0;
				}
				continue;
			}
			else if(current == '/' && (!isAtEnd() && text.charAt(index) == '/')) {
				do {
					advance();
				} while(!isAtEnd() && current != '\n');
				line++;
				continue;
			}
			else if(current == '/' && (!isAtEnd() && text.charAt(index) == '*')) {
				while(true) {
					if(current == '*' && (!isAtEnd() && text.charAt(index) == '/')) {
						advance();
						break;
					}
					advance();
					if(current == '\n') line++;
				}
				continue;
			}
			else if(isValidIdentifierStart(current)) {
				token = identifier();
			}
			else if(isNumeric(current)) {
				token = number();
			}
			else if(current == '"') {
				token = string();
			}
			else if(current == '\'') {
				token = character();
			}
			else {
				TokenType type = switch (current) {
					case '{' -> TokenType.LBRACE;
					case '}' -> TokenType.RBRACE;
					case '(' -> TokenType.LPAREN;
					case ')' -> TokenType.RPAREN;
					case '[' -> TokenType.LSQBR;
					case ']' -> TokenType.RSQBR;
					case ';' -> TokenType.SEMI;
					case ',' -> TokenType.COMMA;
					case '+' -> next('=') ? TokenType.IN_PLUS : TokenType.PLUS;
					case '-' -> next('>') ? TokenType.ARROW : next('=') ? TokenType.IN_MINUS :TokenType.MINUS;
					case '*' -> next('=') ? TokenType.IN_MUL : TokenType.STAR;
					case '/' -> next('=') ? TokenType.IN_DIV : TokenType.SLASH;
					case '%' -> next('=') ? TokenType.IN_MOD : TokenType.PERCENT;
					case '=' -> next('=') ? (next('=') ? TokenType.TRI_EQ : TokenType.EQEQ) : TokenType.EQUALS;
					case ':' -> TokenType.COLON;
					case '.' -> TokenType.DOT;
					case '!' -> next('=') ? (next('=') ? TokenType.TRI_EXEQ : TokenType.EXEQ) : TokenType.EXCLAIM;
					case '<' -> next('=') ? TokenType.LESS_EQ : TokenType.LESS;
					case '>' -> next('=') ? TokenType.GREATER_EQ : TokenType.GREATER;
					case '&' -> next('&') ? TokenType.LOGICAL_AND : TokenType.BITWISE_AND;
					case '|' -> next('|') ? TokenType.LOGICAL_OR : TokenType.BITWISE_OR;
					default -> TokenType.ERROR;
				};
				token = makeToken(type);
			}

			tokens.add(token);
		}
		tokens.add(makeToken(TokenType.EOF));

		return tokens;
	}

	/**
	 * Consumes a string, surrounded by double-quotes (").
	 * @return The consumed tokens.
	 */
	private Token string() {
		advance();

		boolean escape = false;
		while(!isAtEnd()) {
			if(current == '"' && !escape) break;

			if(current == '\n') line++;
			if(escape) escape = false;
			if(current == '\\') escape = true;

			advance();
		}

		return makeToken(TokenType.STRING);
	}

	/**
	 * Consumes a character literal
	 * @return The character token
	 */
	private Token character() {
		advance();

		boolean escape = false;
		while(!isAtEnd()) {
			if(current == '\'' && !escape) break;

			if(current == '\n') line++;
			if(escape) escape = false;
			if(current == '\\') escape = true;

			advance();
		}

		return makeToken(TokenType.CHAR_LITERAL);
	}

	/**
	 * Consumes a single identifier, returning a token with a type of either IDENTIFIER or the corresponding keyword.
	 * A identifier is valid if it starts with {@link #isValidIdentifierStart(char)}
	 * and all following chars are correct, as defined by {@link #isValidIdentifierPart(char)}
	 * @return The consumed identifier token.
	 */
	private Token identifier() {
		while(!isAtEnd() && isValidIdentifierPart(current)) {
			advance();
		}
		if(!isAtEnd()) index--;
		String val = text.substring(start, index);

		return makeToken(switch (val) {
			case "import" -> TokenType.IMPORT;
			case "package" -> TokenType.PACKAGE;
			case "function" -> TokenType.FUNCTION;
			case "var" -> TokenType.VAR;
			case "const" -> TokenType.CONST;
			case "class" -> TokenType.CLASS;
			case "enum" -> TokenType.ENUM;
			case "constructor" -> TokenType.CONSTRUCTOR;
			case "as" -> TokenType.AS;
			case "return" -> TokenType.RETURN;
			case "while" -> TokenType.WHILE;
			case "for" -> TokenType.FOR;
			case "if" -> TokenType.IF;
			case "else" -> TokenType.ELSE;
			case "new" -> TokenType.NEW;
			case "true" -> TokenType.TRUE;
			case "false" -> TokenType.FALSE;
			case "null" -> TokenType.NULL;
			case "this" -> TokenType.THIS;
			case "super" -> TokenType.SUPER;
			case "void" -> TokenType.VOID;
			case "int" -> TokenType.INT;
			case "double" -> TokenType.DOUBLE;
			case "float" -> TokenType.FLOAT;
			case "boolean" -> TokenType.BOOLEAN;
			case "char" -> TokenType.CHAR;
			case "long" -> TokenType.LONG;
			case "byte" -> TokenType.BYTE;
			case "short" -> TokenType.SHORT;
			case "public" -> TokenType.PUBLIC;
			case "private" -> TokenType.PRIVATE;
			case "static" -> TokenType.STATIC;
			case "throw" -> TokenType.THROW;
			case "throws" -> TokenType.THROWS;
			case "try" -> TokenType.TRY;
			case "catch" -> TokenType.CATCH;
			case "finally" -> TokenType.FINALLY;
			default -> TokenType.IDENTIFIER;
		});
	}

	/**
	 * Consumes a number, in form (regex): [0-9]+(\.[0-9]+)?f?
	 * @return The consumed token
	 */
	private Token number() {
		while (!isAtEnd() && isNumeric(current)) {
			advance();
		}
		if(!isAtEnd()) index--;
		if(current == 'l' || current == 'L') {
			advance();
			return makeToken(TokenType.NUMBER);
		}
		if(match('.')) {
			advance();
			while (!isAtEnd() && isNumeric(current)) {
				advance();
			}
			if(!isAtEnd()) index--;
		}
		if(current == 'f' || current == 'F') advance();
		return makeToken(TokenType.NUMBER);
	}

	/**
	 * Matches then consumes a single character.
	 * @param c The character to compare and consume
	 * @return If the character was consumed
	 */
	private boolean match(char c) {
		return current == c && (advance() != -1);
	}

	/**
	 * Tests if the next character is a certain character, then consumes if it is.
	 * @param c The character to compare to.
	 * @return If a consumption occurred
	 */
	private boolean next(char c) {
		return text.charAt(index) == c && advance() != -1;
	}

	/**
	 * Tests if the lexer has reached the end of the string
	 * @return If at EOF
	 */
	private boolean isAtEnd() {
		return index == text.length();
	}

	/**
	 * Consume the next character.
	 * @return The consumed character.
	 */
	private char advance() {
		current = text.charAt(index);
		column++;
		return text.charAt(index++);
	}

	/**
	 * Create a token, from the end of the last one (without whitespace) to the current position, with the correct type.
	 * @param type The token type.
	 * @return The created token.
	 */
	private Token makeToken(TokenType type) {
		Token token = new Token(type, text.substring(start, index), line, column);
		return token;
	}

	/**
	 * Tests if the character matches the regex [0-9]
	 * @param c The character to test
	 * @return If the character is a digit
	 */
	private static boolean isNumeric(char c) {
		return '0' <= c && c <= '9';
	}

	/**
	 * Tests if the character matches the regex [a-zA-Z]
	 * @param c The character to test
	 * @return If the character is an alphabetic character
	 */
	private static boolean isAlpha(char c) {
		return ('a' <= c && c <= 'z') || ('A' <= c && c <= 'Z');
	}

	/**
	 * Tests if an identifier could start with the character
	 * Equivalent regex: [a-zA-Z_]
	 * @param c The character to test
	 * @return If the character matches
	 */
	private static boolean isValidIdentifierStart(char c) {
		return isAlpha(c) || c == '_';
	}

	/**
	 * Tests if an identifier part could contain the character
	 * Equivalent regex: [a-zA-Z0-9_]
	 * @param c The character to test
	 * @return If the character matches
	 */
	private static boolean isValidIdentifierPart(char c) {
		return isAlpha(c) || isNumeric(c) || c == '_';
	}

	/**
	 * Tests if a character is whitespace.
	 * Equivalent regex: [ \r\t\n]
	 * @param c The character to test
	 * @return If the character is whitespace
	 */
	private static boolean isWhitespace(char c) {
		return c == ' ' || c == '\r' || c == '\t' || c == '\n';
	}

}
