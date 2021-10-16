package water.compiler.parser.nodes.classes;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.*;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.variable.VariableDeclarationNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ClassDeclarationNode implements Node {

	private final Token name;
	private final List<Node> declarations;
	private final List<ConstructorDeclarationNode> constructors;
	private final Token access;
	private boolean staticVariableInit;

	public ClassDeclarationNode(Token name, List<Node> declarations, Token access) {
		this.name = name;
		this.declarations = declarations;
		this.constructors = new ArrayList<>();
		this.access = access;
		this.staticVariableInit = false;
	}

	@Override
	public void buildClasses(Context context) {
		ContextType prevType = context.getType();
		String prevClass = context.getCurrentClass();

		ClassWriter writer = initClass(context);

		writer.visitEnd();

		context.setType(prevType);
		context.setCurrentClass(prevClass);
	}

	@Override
	public void visit(FileContext fc) throws SemanticException {
		Context context = fc.getContext();
		ContextType prevType = context.getType();
		String prevClass = context.getCurrentClass();

		ClassWriter writer = initClass(context);

		MethodVisitor defaultConstructor = null;
		if(constructors.size() == 0) {
			defaultConstructor = createDefaultConstructor(writer);

			context.setDefaultConstructor(defaultConstructor);
		}

		MethodVisitor staticMethod = null;
		if(staticVariableInit) {
			staticMethod = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			context.setStaticMethodVisitor(staticMethod);

			staticMethod.visitCode();
		}

		Scope outer = context.getScope();
		context.setScope(outer.nextDepth());
		context.getScope().updateCurrentClassMethods(fc);

		for(Node declaration : declarations) {
			declaration.visit(fc);
		}

		context.setConstructors(new ArrayList<>());
		for(ConstructorDeclarationNode constructor : constructors) {
			constructor.visit(fc);
		}

		if(defaultConstructor != null) {
			defaultConstructor.visitInsn(Opcodes.RETURN);
			defaultConstructor.visitMaxs(0, 0);
			defaultConstructor.visitEnd();
		}

		if(staticVariableInit) {
			staticMethod.visitInsn(Opcodes.RETURN);
			staticMethod.visitMaxs(0, 0);
			staticMethod.visitEnd();
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

		MethodVisitor defaultConstructor = null;

		Scope outer = context.getScope();
		context.setScope(outer.nextDepth());

		for(int i = 0; i < declarations.size(); i++) {
			Node declaration = declarations.get(i);
			if(declaration instanceof ConstructorDeclarationNode) {
				constructors.add((ConstructorDeclarationNode) declaration);
				declarations.remove(i);
				declaration.preprocess(context);
			}
		}

		if(constructors.size() == 0) {
			defaultConstructor = createDefaultConstructor(writer);

			context.setDefaultConstructor(defaultConstructor);
		}
		context.setConstructors(constructors);

		for (Node declaration : declarations) {
			declaration.preprocess(context);
			if (declaration instanceof VariableDeclarationNode && ((VariableDeclarationNode) declaration).isStatic(context))
				staticVariableInit = true;
		}

		if(defaultConstructor != null) {
			defaultConstructor.visitInsn(Opcodes.RETURN);
			defaultConstructor.visitMaxs(0, 0);
			defaultConstructor.visitEnd();
		}

		if(staticVariableInit) {
			MethodVisitor staticMethod = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			staticMethod.visitCode();
			staticMethod.visitInsn(Opcodes.RETURN);
			staticMethod.visitMaxs(0, 0);
			staticMethod.visitEnd();
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

		return constructor;
	}

	private int getAccessLevel() {
		if(access == null || access.getType() == TokenType.PUBLIC) return Opcodes.ACC_PUBLIC;
		return 0;
	}

	public String getName() {
		return name.getValue();
	}

	@Override
	public boolean isNewClass() {
		return true;
	}

	@Override
	public String toString() {
		return "class %s {%s}".formatted(name.getValue(), declarations.stream().map(Node::toString).collect(Collectors.joining()));
	}
}
