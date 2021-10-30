package water.compiler.parser;

import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Lexer;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.nodes.block.BlockNode;
import water.compiler.parser.nodes.block.ProgramNode;
import water.compiler.parser.nodes.classes.*;
import water.compiler.parser.nodes.exception.CatchNode;
import water.compiler.parser.nodes.exception.ThrowNode;
import water.compiler.parser.nodes.exception.TryNode;
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
import java.util.LinkedList;
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
	private boolean isParsingClass;

	/**
	 * Produces a syntax tree based on tokens outputted by {@link Lexer}.
	 * @param tokens A list of tokens produced from source code.
	 * @return An AST
	 * @throws UnexpectedTokenException If a parser error occurs, i.e. a token which does not grammatically make sense.
	 */
	public Node parse(List<Token> tokens) throws UnexpectedTokenException {
		this.tokens = tokens;
		this.index = 0;
		this.isParsingClass = false;

		return program();
	}

	/**
	 * Top level declarations
	 * @return The full AST of the program
	 */
	private Node program() throws UnexpectedTokenException {
		ArrayList<Node> declarations = new ArrayList<>();
		ArrayList<Node> imports = new ArrayList<>();

		Node packageName = null;

		// A package statement must appear first in a file
		if(match(TokenType.PACKAGE)) {
			packageName = packageStatement();
		}

		// All import statements must appear at the top of the file, before standard declarations
		while(!isAtEnd() && match(TokenType.IMPORT)) {
			imports.add(importStatement());
		}

		// Top level declarations
		while(!isAtEnd()) {
			declarations.add(declaration());
		}
		return new ProgramNode(packageName, imports, declarations);
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
	 *  Forms grammar: functionDeclaration | variableDeclaration | classDeclaration | enumDeclaration | constructorDeclaration
	 */
	private Node declaration() throws UnexpectedTokenException {
		Token accessModifier = null;
		Token staticModifier = null;

		if(match(TokenType.PUBLIC) || match(TokenType.PRIVATE)) {
			accessModifier = tokens.get(index - 1);
		}

		if(isParsingClass && match(TokenType.STATIC)) {
			staticModifier = tokens.get(index - 1);
		}

		Token tok = advance();
		return switch(tok.getType()) {
			case FUNCTION -> functionDeclaration(accessModifier, staticModifier);
			case CLASS -> classDeclaration(accessModifier, staticModifier);
			case ENUM -> enumDeclaration(accessModifier, staticModifier);
			case CONSTRUCTOR -> constructorDeclaration(accessModifier, staticModifier);
			case VAR, CONST -> variableDeclaration(accessModifier, staticModifier);
			default -> throw new UnexpectedTokenException(tok, "Expected declaration");
		};
	}

	/** Forms grammar: 'function' IDENTIFIER typedParameters (('->' type)? throws blockStatement) | (throws '=' expression ';')
	 *  WHERE throws: ('throws' basicType (',' basicType*))? */
	private Node functionDeclaration(Token access, Token staticModifier) throws UnexpectedTokenException {
		Token name = consume(TokenType.IDENTIFIER, "Expected function name");

		List<Pair<Token, Node>> parameters = typedParameters("function parameter list");

		Node returnType = null;

		if(match(TokenType.ARROW)) {
			if(match(TokenType.VOID)) returnType = new TypeNode(tokens.get(index - 1));
			else returnType = type();
		}

		List<Node> throwsList = null;
		if(match(TokenType.THROWS)) {
			throwsList = new ArrayList<>();
			do {
				throwsList.add(basicType());
			} while(match(TokenType.COMMA));
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

		return new FunctionDeclarationNode(type, name, body, parameters, returnType, throwsList, access, staticModifier);
	}

	/** Forms grammar: 'constructor' typedParameters (':' arguments)? blockStatement */
	private Node constructorDeclaration(Token access, Token staticModifier) throws UnexpectedTokenException {
		if(!isParsingClass) throw new UnexpectedTokenException(tokens.get(index - 1), "Cannot declare constructor outside of class");
		if(staticModifier != null) throw new UnexpectedTokenException(staticModifier, "Cannot declare constructor as static");

		Token constructor = tokens.get(index - 1);

		List<Pair<Token, Node>> parameters = typedParameters("constructor parameter list");

		List<Node> superArgs = null;
		if(match(TokenType.COLON)) {
			superArgs = arguments("super arguments");
		}

		consume(TokenType.LBRACE, "Expected '{' before constructor body");

		Node body = blockStatement();

		return new ConstructorDeclarationNode(access, constructor, superArgs, parameters, body);
	}

	/** Forms grammar: 'class' IDENTIFIER '{' declaration* '}' */
	private Node classDeclaration(Token access, Token staticModifier) throws UnexpectedTokenException {
		if(staticModifier != null) throw new UnexpectedTokenException(staticModifier, "Cannot mark a class as static");
		Token name = consume(TokenType.IDENTIFIER, "Expected class name");

		Node superclass = null;
		if(match(TokenType.COLON)) {
			superclass = basicType();
		}

		consume(TokenType.LBRACE, "Expected '{' before class body");

		LinkedList<Node> declarations = new LinkedList<>();

		boolean wasParsingClass = isParsingClass;
		isParsingClass = true;
		while(!isAtEnd() && tokens.get(index).getType() != TokenType.RBRACE) {
			declarations.add(declaration());
		}
		isParsingClass = wasParsingClass;

		consume(TokenType.RBRACE, "Expected '}' after class body");

		return new ClassDeclarationNode(name, superclass, declarations, access);
	}

	/** Forms grammar: 'enum' '{' (IDENTIFIER (',' IDENTIFIER)*)? '}' */
	private Node enumDeclaration(Token access, Token staticModifier) throws UnexpectedTokenException {
		if(staticModifier != null) throw new UnexpectedTokenException(staticModifier, "Cannot mark an enum as static");
		Token name = consume(TokenType.IDENTIFIER, "Expected enum name");

		consume(TokenType.LBRACE, "Expected '{' before enum body");

		ArrayList<Token> fields = new ArrayList<>();

		if(tokens.get(index).getType() != TokenType.RBRACE) {
			do {
				fields.add(consume(TokenType.IDENTIFIER, "Expected enum field name"));
			} while (!isAtEnd() && match(TokenType.COMMA));
		}

		consume(TokenType.RBRACE, "Expected '}' after enum body");

		return new EnumDeclarationNode(name, fields, access);
	}

	/** Forms grammar: ('var' | 'const') IDENTIFIER '=' expression ';' */
	private Node variableDeclaration(Token access, Token staticModifier) throws UnexpectedTokenException {
		boolean isConst = tokens.get(index - 1).getType() == TokenType.CONST;
		Token name = consume(TokenType.IDENTIFIER, "Expected variable name");

		Node type = null;
		Node value = null;
		if(match(TokenType.COLON)) {
			type = type();
			if(match(TokenType.EQUALS)) {
				value = expression();
			}
		}
		else {
			consume(TokenType.EQUALS, "Expected '=' after variable name");

			value = expression();
		}

		consume(TokenType.SEMI, "Expected ';' after variable assignment");

		return new VariableDeclarationNode(name, type, value, isConst, access, staticModifier);
	}

	//============================ Statements =============================

	/** Forms grammar: '{' statement* '}'*/
	private Node blockStatement() throws UnexpectedTokenException {
		ArrayList<Node> nodes = new ArrayList<>();
		if(tokens.get(index).getType() != TokenType.RBRACE) {
			do {
				if(match(TokenType.VAR) || match(TokenType.CONST)) nodes.add(variableDeclaration(null, null));
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

		if(match(TokenType.VAR) || match(TokenType.CONST)) {
			init = variableDeclaration(null, null);
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

	/** Forms grammar: 'throw' expression ';' */
	private Node throwStatement() throws UnexpectedTokenException {
		Token throwTok = consume(TokenType.THROW, "Expected 'throw'");

		Node throwee = expression();

		consume(TokenType.SEMI, "Expected ';' after throw target");

		return new ThrowNode(throwTok, throwee);
	}

	/** Forms grammar: 'try' blockStatement (catch+) | (catch* 'finally' blockStatement)
	 *  WHERE catch: 'catch' '(' IDENTIFIER ':' basicType ')' blockStatement */
	private Node tryStatement() throws UnexpectedTokenException {
		Token tryTok = consume(TokenType.TRY, "Expected 'try'");

		consume(TokenType.LBRACE, "Expected '{' after try");

		Node tryBody = blockStatement();

		ArrayList<CatchNode> catchBlocks = new ArrayList<>();
		while(match(TokenType.CATCH)) {
			consume(TokenType.LPAREN, "Expected '(' after catch");

			Token bindingName = consume(TokenType.IDENTIFIER, "Expected catch exception binding name");

			consume(TokenType.COLON, "Expected ':' between name and type");

			Node exceptionType = basicType();

			consume(TokenType.RPAREN, "Expected ')' after catch clause");

			consume(TokenType.LBRACE, "Expected '{' after catch clause");

			Node catchBody = blockStatement();

			catchBlocks.add(new CatchNode(bindingName, exceptionType, catchBody));
		}

		Node finallyBlock = null;
		if(match(TokenType.FINALLY)) {
			consume(TokenType.LBRACE, "Expected '{' after catch clause");
			finallyBlock = blockStatement();
		}

		if(catchBlocks.size() == 0 && finallyBlock == null) {
			throw new UnexpectedTokenException(tryTok, "'try' block must have at least one catch/finally block");
		}

		return new TryNode(tryBody, catchBlocks, finallyBlock);
	}

	/** Forms grammar: blockStatement | ifStatement | whileStatement | forStatement | returnStatement | throwStatement | tryStatement | expressionStatement */
	private Node statement() throws UnexpectedTokenException {
		return switch (tokens.get(index).getType()) {
			case LBRACE -> { advance(); yield blockStatement(); }
			case IF -> ifStatement();
			case WHILE -> whileStatement();
			case FOR -> forStatement();
			case RETURN -> returnStatement();
			case THROW -> throwStatement();
			case TRY -> tryStatement();
			default -> expressionStatement();
		};
	}

	//============================ Expressions =============================

	/*
	Precedence:
	assignExpr = += (etc)
	logicalOrExpr ||
	logicalAndExpr &&
	bitwiseOrExpr |
	bitwiseXorExpr ^
	bitwiseAndExpr &
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

	/** Forms grammar: logicalOrExpr (('=' | INPLACE_OPERATOR) logicalOrExpr)* */
	private Node assignExpr() throws UnexpectedTokenException {
		Node left = logicalOrExpr();

		while(matchAssignment()) {
			Token op = tokens.get(index - 1);

			Node right = logicalOrExpr();

			left = new AssignmentNode(left, op, right);
		}

		return left;
	}

	/** Forms grammar: logicalAndExpr ('||' logicalAndExpr)* */
	private Node logicalOrExpr() throws UnexpectedTokenException {
		Node left = logicalAndExpr();

		while(match(TokenType.LOGICAL_OR)) {
			Token op = tokens.get(index - 1);

			Node right = logicalAndExpr();

			left = new LogicalOperationNode(left, op, right);
		}
		return left;
	}

	/** Forms grammar: bitwiseOrExpr ('&&' bitwiseOrExpr)* */
	private Node logicalAndExpr() throws UnexpectedTokenException {
		Node left = bitwiseOrExpr();

		while(match(TokenType.LOGICAL_AND)) {
			Token op = tokens.get(index - 1);

			Node right = bitwiseOrExpr();

			left = new LogicalOperationNode(left, op, right);
		}
		return left;
	}

	/** Forms grammar: bitwiseXorExpr ('|' bitwiseXorExpr)* */
	private Node bitwiseOrExpr() throws UnexpectedTokenException {
		Node left = bitwiseXorExpr();

		while(match(TokenType.BITWISE_OR)) {
			Token op = tokens.get(index - 1);

			Node right = bitwiseXorExpr();

			left = new IntegerOperationNode(left, op, right);
		}
		return left;
	}

	/** Forms grammar: bitwiseAndExpr ('^' bitwiseAndExpr)* */
	private Node bitwiseXorExpr() throws UnexpectedTokenException {
		Node left = bitwiseAndExpr();

		while(match(TokenType.BITWISE_XOR)) {
			Token op = tokens.get(index - 1);

			Node right = bitwiseAndExpr();

			left = new IntegerOperationNode(left, op, right);
		}
		return left;
	}

	/** Forms grammar: equalityExpr ('^' equalityExpr)* */
	private Node bitwiseAndExpr() throws UnexpectedTokenException {
		Node left = equalityExpr();

		while(match(TokenType.BITWISE_AND)) {
			Token op = tokens.get(index - 1);

			Node right = equalityExpr();

			left = new IntegerOperationNode(left, op, right);
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

	/** Forms grammar: ('!' | '-') unary | memberAccess */
	private Node unary() throws UnexpectedTokenException {
		if(match(TokenType.EXCLAIM) || match(TokenType.MINUS)) {
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

				left = new MethodCallNode(left, name, args, false);
			}
			else {
				left = new MemberAccessNode(left, name);
			}
		}

		return left;
	}

	/** Forms grammar: NUMBER | STRING | CHAR_LITERAL | 'true' | 'false' | 'null' | 'this' | superCall | newObject | grouping | variable */
	private Node atom() throws UnexpectedTokenException {
		Token tok = advance();
		return switch(tok.getType()) {
			case NUMBER -> new NumberNode(tok);
			case STRING -> new StringNode(tok);
			case CHAR_LITERAL -> new CharNode(tok);
			case TRUE, FALSE -> new BooleanNode(tok);
			case NULL -> new NullNode();
			case THIS -> new ThisNode(tok);
			case SUPER -> superCall();
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

	/** Forms grammar: 'super' '.' IDENTIFIER arguments */
	private Node superCall() throws UnexpectedTokenException {
		Token superTok = tokens.get(index - 1);
		consume(TokenType.DOT, "Expected '.' after super.");

		Token name = consume(TokenType.IDENTIFIER, "Expected super method name");

		List<Node> args = arguments("super arguments");

		return new MethodCallNode(new SuperNode(superTok), name, args, true);
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

	private boolean matchAssignment() {
		switch (tokens.get(index).getType()) {
			case EQUALS, IN_PLUS, IN_MINUS, IN_MUL, IN_DIV, IN_MOD -> {
				advance();
				return true;
			}
		}
		return false;
	}

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
