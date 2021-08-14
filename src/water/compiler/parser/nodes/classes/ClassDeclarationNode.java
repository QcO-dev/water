package water.compiler.parser.nodes.classes;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.ContextType;
import water.compiler.compiler.Scope;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.Node;

import java.util.List;
import java.util.stream.Collectors;

public class ClassDeclarationNode implements Node {

	private final Token name;
	private final List<Node> declarations;
	private final Token access;

	public ClassDeclarationNode(Token name, List<Node> declarations, Token access) {
		this.name = name;
		this.declarations = declarations;
		this.access = access;
	}

	@Override
	public void visit(FileContext fc) throws SemanticException {
		Context context = fc.getContext();
		ContextType prevType = context.getType();
		String prevClass = context.getCurrentClass();

		ClassWriter writer = initClass(context);

		createDefaultConstructor(writer);

		Scope outer = context.getScope();
		context.setScope(outer.nextDepth());
		context.getScope().updateCurrentClassMethods(fc);

		for(Node declaration : declarations) {
			declaration.visit(fc);
		}

		writer.visitEnd();

		context.setScope(outer);
		context.setType(prevType);
		context.setCurrentClass(prevClass);
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		ContextType prevType = context.getType();
		String prevClass = context.getCurrentClass();

		ClassWriter writer = initClass(context);

		createDefaultConstructor(writer);

		Scope outer = context.getScope();
		context.setScope(outer.nextDepth());

		for(Node declaration : declarations) {
			declaration.preprocess(context);
		}

		writer.visitEnd();

		context.setScope(outer);
		context.setType(prevType);
		context.setCurrentClass(prevClass);
	}

	private ClassWriter initClass(Context context) {
		context.setType(ContextType.CLASS);
		int accessLevel = getAccessLevel();

		String baseName = name.getValue();
		String className = baseName;

		// "private" class
		if(accessLevel == 0) {
			className = context.getCurrentClass() + "$" + baseName;
		}
		if(!context.getPackageName().isEmpty()) {
			className = context.getPackageName() + "/" + className;
		}
		if(!className.equals(baseName)) {
			context.getImports().put(baseName, className.replace('/', '.'));
		}

		context.setCurrentClass(className);

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

		writer.visit(Opcodes.V9, accessLevel | Opcodes.ACC_SUPER, className, null, "java/lang/Object", null);

		writer.visitSource(context.getSource(), null);

		context.getClassWriterMap().put(className, writer);

		return writer;
	}

	private MethodVisitor createDefaultConstructor(ClassWriter writer) {
		MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		constructor.visitCode();

		constructor.visitVarInsn(Opcodes.ALOAD, 0);
		constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		constructor.visitInsn(Opcodes.RETURN);

		constructor.visitMaxs(0, 0);
		constructor.visitEnd();

		return constructor;
	}

	private int getAccessLevel() {
		if(access == null || access.getType() == TokenType.PUBLIC) return Opcodes.ACC_PUBLIC;
		return 0;
	}

	@Override
	public String toString() {
		return "class %s {%s}".formatted(name.getValue(), declarations.stream().map(Node::toString).collect(Collectors.joining()));
	}
}
