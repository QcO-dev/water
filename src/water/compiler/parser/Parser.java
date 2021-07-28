package water.compiler.parser;

import water.compiler.lexer.Lexer;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.nodes.block.BlockNode;
import water.compiler.parser.nodes.block.ProgramNode;
import water.compiler.parser.nodes.classes.MemberAccessNode;
import water.compiler.parser.nodes.classes.MethodCallNode;
import water.compiler.parser.nodes.classes.ObjectConstructorNode;
import water.compiler.parser.nodes.function.FunctionCallNode;
import water.compiler.parser.nodes.function.FunctionDeclarationNode;
import water.compiler.parser.nodes.operation.*;
import water.compiler.parser.nodes.special.ImportNode;
import water.compiler.parser.nodes.special.PackageNode;
import water.compiler.parser.nodes.statement.*;
import water.compiler.parser.nodes.value.*;
import water.compiler.parser.nodes.variable.AssignmentNode;
import water.compiler.parser.nodes.variable.VariableAccessNode;
import water.compiler.parser.nodes.variable.VariableDeclarationNode;
import water.compiler.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a syntax tree based from a list of tokens.
 *
 * This syntax tree is not abstract as the nodes encode how they should be compiled,
 * however the term AST may be used throughout.
 *
 */
public class Parser {

	private int index;
	private List<Token> tokens;

	/**
	 * Produces a syntax tree based on tokens outputted by {@link Lexer}.
	 * @param tokens A list of tokens produced from source code.
	 * @return An AST
	 * @throws UnexpectedTokenException If a parser error occurs, i.e. a token which does not grammatically make sense.
	 */
	public Node parse(List<Token> tokens) throws UnexpectedTokenException {
		this.tokens = tokens;
		this.index = 0;

		return program();
	}

	/**
	 * Top level declarations
	 * @return The full AST of the program
	 */
	private Node program() throws UnexpectedTokenException {
		ArrayList<Node> declarations = new ArrayList<>();

		Node packageName = null;

		// A package statement must appear first in a file
		if(match(TokenType.PACKAGE)) {
			packageName = packageStatement();
		}

		// All import statements must appear at the top of the file, before standard declarations
		while(!isAtEnd() && match(TokenType.IMPORT)) {
			declarations.add(importStatement());
		}

		// Top level declarations
		while(!isAtEnd()) {
			declarations.add(declaration());
		}
		return new ProgramNode(packageName, declarations);
	}

	//============================ Special Statements =============================

	/** Forms grammar: 'package' classType ';' */
	private Node packageStatement() throws UnexpectedTokenException {
		Token packageToken = tokens.get(index - 1);

		Node name = classType();

		consume(TokenType.SEMI, "Expected ';' after package");

		return new PackageNode(packageToken, (TypeNode) name);
	}

	/** Forms grammar: 'import' classType ';' */
	private Node importStatement() throws UnexpectedTokenException {
		Token importTok = tokens.get(index - 1);
		Node type = classType();

		consume(TokenType.SEMI, "Expected ';' after import");

		return new ImportNode(importTok, type);
	}


	//============================ Declarations =============================

	/**
	 *  Declarations used within classes / top level
	 *  Forms grammar: functionDeclaration | variableDeclaration
	 */
	private Node declaration() throws UnexpectedTokenException {
		Token tok = advance();
		return switch(tok.getType()) {
			case FUNCTION -> functionDeclaration();
			case VAR -> variableDeclaration();
			default -> throw new UnexpectedTokenException(tok, "Expected declaration");
		};
	}

	/** Forms grammar: 'function' IDENTIFIER typedParameters (('->' type)? blockStatement) | ('=' expression ';') */
	private Node functionDeclaration() throws UnexpectedTokenException {
		Token name = consume(TokenType.IDENTIFIER, "Expected function name");

		List<Pair<Token, Node>> parameters = typedParameters("function parameter list");

		Node returnType = null;

		if(match(TokenType.ARROW)) {
			if(match(TokenType.VOID)) returnType = new TypeNode(tokens.get(index - 1));
			else returnType = type();
		}

		Node body;
		FunctionDeclarationNode.DeclarationType type = FunctionDeclarationNode.DeclarationType.STANDARD;

		if(match(TokenType.EQUALS)) {
			body = expression();
			consume(TokenType.SEMI, "Expected ';' after expression");
			type = FunctionDeclarationNode.DeclarationType.EXPRESSION;
		}
		else {
			consume(TokenType.LBRACE, "Expected '{' before function body");

			body = blockStatement();
		}

		return new FunctionDeclarationNode(type, name, body, parameters, returnType);
	}

	/** Forms grammar: 'var' IDENTIFIER '=' expression ';' */
	private Node variableDeclaration() throws UnexpectedTokenException {
		Token name = consume(TokenType.IDENTIFIER, "Expected variable name");

		consume(TokenType.EQUALS, "Expected '=' after variable name");

		Node value = expression();

		consume(TokenType.SEMI, "Expected ';' after variable assignment");

		return new VariableDeclarationNode(name, value);
	}

	//============================ Statements =============================

	/** Forms grammar: '{' statement* '}'*/
	private Node blockStatement() throws UnexpectedTokenException {
		ArrayList<Node> nodes = new ArrayList<>();
		if(tokens.get(index).getType() != TokenType.RBRACE) {
			do {
				if(match(TokenType.VAR)) nodes.add(variableDeclaration());
				else nodes.add(statement());
			} while (!isAtEnd() && tokens.get(index).getType() != TokenType.RBRACE);
		}

		consume(TokenType.RBRACE, "Expected '}' after block");
		return new BlockNode(nodes);
	}

	/** Forms grammar: expression ';' */
	private Node expressionStatement() throws UnexpectedTokenException {
		Node expr = expression();
		consume(TokenType.SEMI, "Expected ';' after expression");
		return new ExpressionStatementNode(expr);
	}

	/** Forms grammar: 'return' expression? ';' */
	private Node returnStatement() throws UnexpectedTokenException {
		Token returnTok = consume(TokenType.RETURN, "Expected 'return'");
		Node expression = null;
		if(tokens.get(index).getType() != TokenType.SEMI) {
			expression = expression();
		}
		consume(TokenType.SEMI, "Expected ';' after return");

		return new ReturnNode(returnTok, expression);
	}

	/** Forms grammar: 'if' '(' expression ')' statement ('else' statement)? */
	private Node ifStatement() throws UnexpectedTokenException {
		Token ifTok = consume(TokenType.IF, "Expected 'if'");

		consume(TokenType.LPAREN, "Expected '(' after if");

		Node condition = expression();

		consume(TokenType.RPAREN, "Expected ')' after condition");

		Node body = statement();

		Node elseBody = null;

		if(match(TokenType.ELSE)) {
			elseBody = statement();
		}

		return new IfStatementNode(ifTok, condition, body, elseBody);
	}

	/** Forms grammar: 'while' '(' expression ')' statement */
	private Node whileStatement() throws UnexpectedTokenException {
		Token whileTok = consume(TokenType.WHILE, "Expected 'while'");

		consume(TokenType.LPAREN, "Expected '(' after while");

		Node condition = expression();

		consume(TokenType.RPAREN, "Expected ')' after condition");

		Node body = statement();

		return new WhileStatementNode(whileTok, condition, body);
	}

	/** Forms grammar: 'for' '(' (variableDeclaration | expressionStatement) expression ';' expression ')' statement */
	private Node forStatement() throws UnexpectedTokenException {
		Token forTok = consume(TokenType.FOR, "Expected 'for'");

		consume(TokenType.LPAREN, "Expected '(' after for");

		Node init;

		if(match(TokenType.VAR)) {
			init = variableDeclaration();
		}
		else {
			init = expressionStatement();
		}

		Node condition = expression();

		consume(TokenType.SEMI, "Expected ';' after condition");

		Node iterate = expression();

		consume(TokenType.RPAREN, "Expected ')' before for body");

		Node body = statement();

		return new ForStatementNode(forTok, init, condition, iterate, body);
	}

	/** Forms grammar: blockStatement | ifStatement | whileStatement | forStatement | returnStatement | expressionStatement */
	private Node statement() throws UnexpectedTokenException {
		return switch (tokens.get(index).getType()) {
			case LBRACE -> { advance(); yield blockStatement(); }
			case IF -> ifStatement();
			case WHILE -> whileStatement();
			case FOR -> forStatement();
			case RETURN -> returnStatement();
			default -> expressionStatement();
		};
	}

	//============================ Expressions =============================

	/*
	Precedence:
	assignExpr =
	equalityExpr == != === !==
	relativeExpr < <= > >=
	arithExpr + -
	term * / %
	atom (value)
	 */

	/** Wrapper around the lowest precedence expression type */
	private Node expression() throws UnexpectedTokenException {
		return assignExpr();
	}

	/** Forms grammar: equalityExpr ('=' equalityExpr)* */
	private Node assignExpr() throws UnexpectedTokenException {
		Node left = equalityExpr();

		while(match(TokenType.EQUALS)) {
			Token op = tokens.get(index - 1);

			Node right = equalityExpr();

			left = new AssignmentNode(left, op, right);
		}

		return left;
	}

	/** Forms grammar: relativeExpr (('==' | '!=' | '===' | '!==') relativeExpr)* */
	private Node equalityExpr() throws UnexpectedTokenException {
		Node left = relativeExpr();

		while(match(TokenType.EQEQ) || match(TokenType.EXEQ) || match(TokenType.TRI_EQ) || match(TokenType.TRI_EXEQ)) {
			Token op = tokens.get(index - 1);

			Node right = relativeExpr();

			left = new EqualityOperationNode(left, op, right);
		}
		return left;
	}

	/** Forms grammar: arithExpr (('<' | '<=' | '>' '>=') arithExpr)* */
	private Node relativeExpr() throws UnexpectedTokenException {
		Node left = arithExpr();

		while(match(TokenType.LESS) || match(TokenType.LESS_EQ) || match(TokenType.GREATER) || match(TokenType.GREATER_EQ)) {
			Token op = tokens.get(index - 1);

			Node right = arithExpr();

			left = new RelativeOperationNode(left, op, right);
		}
		return left;
	}

	/** Forms grammar: term (('+' | '-') term)* */
	private Node arithExpr() throws UnexpectedTokenException {
		Node left = term();

		while(match(TokenType.PLUS) || match(TokenType.MINUS)) {
			Token op = tokens.get(index - 1);

			Node right = term();

			left = new ArithmeticOperationNode(left, op, right);
		}
		return left;
	}

	/** Forms grammar: cast (('*' | '/' | '%') cast)* */
	private Node term() throws UnexpectedTokenException {
		Node left = cast();

		while(match(TokenType.STAR) || match(TokenType.SLASH) || match(TokenType.PERCENT)) {
			Token op = tokens.get(index - 1);

			Node right = cast();

			left = new ArithmeticOperationNode(left, op, right);
		}
		return left;
	}

	/** Forms grammar: unary ('as' type)* */
	private Node cast() throws UnexpectedTokenException {
		Node left = unary();

		while(match(TokenType.AS)) {
			Token as = tokens.get(index - 1);
			Node type = type();

			left = new CastNode(left, type, as);
		}

		return left;
	}

	/** Forms grammar: '!' unary | atom */
	private Node unary() throws UnexpectedTokenException {
		if(match(TokenType.EXCLAIM)) {
			Token op = tokens.get(index - 1);

			Node right = unary();

			return new UnaryOperationNode(op, right);
		}
		return memberAccess();
	}

	/** Forms grammar: atom (('.' IDENTIFIER arguments?) | ('[' expression ']'))* */
	private Node memberAccess() throws UnexpectedTokenException {
		Node left = atom();

		while(match(TokenType.DOT) || match(TokenType.LSQBR)) {
			if(tokens.get(index - 1).getType() == TokenType.LSQBR) {
				Token bracket = tokens.get(index - 1);
				Node index = expression();
				consume(TokenType.RSQBR, "Expected ']' after index");
				left = new IndexAccessNode(bracket, left, index);
				continue;
			}

			Token name = consume(TokenType.IDENTIFIER, "Expected member name");

			if(tokens.get(index).getType() == TokenType.LPAREN) {
				List<Node> args = arguments("method arguments");

				left = new MethodCallNode(left, name, args);
			}
			else {
				left = new MemberAccessNode(left, name);
			}
		}

		return left;
	}

	/** Forms grammar: NUMBER | STRING | 'true' | 'false' | 'null' | newObject | grouping | variable */
	private Node atom() throws UnexpectedTokenException {
		Token tok = advance();
		return switch(tok.getType()) {
			case NUMBER -> new NumberNode(tok);
			case STRING -> new StringNode(tok);
			case TRUE, FALSE -> new BooleanNode(tok);
			case NULL -> new NullNode();
			case NEW -> newObject();
			case LPAREN -> grouping();
			case IDENTIFIER -> variable();
			default -> throw new UnexpectedTokenException(tok, "Expected value");
		};
	}

	/** Forms grammar: type arguments */
	private Node newObject() throws UnexpectedTokenException {
		Token newToken = tokens.get(index - 1);
		Node type = basicType();

		if(match(TokenType.LSQBR)) {
			return newArray(newToken, type);
		}

		List<Node> args = arguments("constructor arguments");

		return new ObjectConstructorNode(newToken, type, args);
	}

	private Node newArray(Token newToken, Node type) throws UnexpectedTokenException {
		ArrayList<Node> dimensions = new ArrayList<>();

		do {
			dimensions.add(expression());
			consume(TokenType.RSQBR, "Expected ']' after array size");
		} while(match(TokenType.LSQBR));


		return new ArrayConstructorNode(newToken, type, dimensions);
	}

	/** Forms grammar: '(' expression ')' */
	private Node grouping() throws UnexpectedTokenException {
		Node val = expression();
		consume(TokenType.RPAREN, "Expected ')' after expression");
		return new GroupingNode(val);
	}

	/** Forms grammar: IDENTIFIER arguments? */
	private Node variable() throws UnexpectedTokenException {
		Token name = tokens.get(index - 1);

		if(tokens.get(index).getType() == TokenType.LPAREN) {
			List<Node> args = arguments("function arguments");

			return new FunctionCallNode(name, args);
		}
		return new VariableAccessNode(name);
	}

	//============================ Utility ============================

	/** Forms grammar: basicType('[' ']')* */
	private Node type() throws UnexpectedTokenException {
		Node type = basicType();

		int dim = 0;
		while(match(TokenType.LSQBR)) {
			consume(TokenType.RSQBR, "Expected closing ']'");
			dim++;
		}

		if(dim != 0) {
			type = new TypeNode((TypeNode) type, dim);
		}

		return type;
	}

	private Node basicType() throws UnexpectedTokenException {
		if(Lexer.PRIMITIVE_TYPES.contains(tokens.get(index).getType())) {
			return new TypeNode(advance());
		}
		else {
			return classType();
		}
	}

	/** Forms grammar: IDENTIFIER ('.' IDENTIFIER)* */
	private Node classType() throws UnexpectedTokenException {
		ArrayList<String> parts = new ArrayList<>();
		Token start = tokens.get(index);
		do {
			Token part = consume(TokenType.IDENTIFIER, "Expected class name");
			parts.add(part.getValue());
		} while(match(TokenType.DOT));
		return new TypeNode(start, String.join(".", parts));
	}

	/** Forms grammar: '(' (IDENTIFIER ':' type (COMMA IDENTIFIER ':' type)*)? ')'*/
	private List<Pair<Token, Node>> typedParameters(String name) throws UnexpectedTokenException {
		consume(TokenType.LPAREN, "Expected '(' before " + name);
		ArrayList<Pair<Token, Node>> parameters = new ArrayList<>();
		if(tokens.get(index).getType() != TokenType.RPAREN) {
			do {
				Token parameterName = consume(TokenType.IDENTIFIER, "Expected parameter name");

				consume(TokenType.COLON, "Expected ':' between parameter name and type");

				Node type = type();

				parameters.add(new Pair<>(parameterName, type));
			} while(match(TokenType.COMMA));
		}
		consume(TokenType.RPAREN, "Expected ')' after " + name);

		return parameters;
	}

	/** Forms grammar: '(' (expression (COMMA expression)*)? ')' */
	private List<Node> arguments(String name) throws UnexpectedTokenException {
		consume(TokenType.LPAREN, "Expected '(' before " + name);

		ArrayList<Node> args = new ArrayList<>();
		if(tokens.get(index).getType() != TokenType.RPAREN) {
			do {
				args.add(expression());
			} while (!isAtEnd() && match(TokenType.COMMA));
		}

		consume(TokenType.RPAREN, "Expected ')' after " + name);

		return args;
	}

	//============================ Helpers =============================

	private boolean match(TokenType type) {
		if(tokens.get(index).getType() != type) return false;
		advance();
		return true;
	}

	private Token consume(TokenType type, String message) throws UnexpectedTokenException {
		Token tok = tokens.get(index);
		if(tok.getType() != type) throw new UnexpectedTokenException(tok, message);
		return advance();
	}

	private boolean isAtEnd() {
		return tokens.get(index).getType() == TokenType.EOF;
	}

	private Token advance() {
		return tokens.get(index++);
	}
}
