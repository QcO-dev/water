package water.compiler.parser.nodes.classes;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.ContextType;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.Node;

public class ClassDeclarationNode implements Node {

	private final Token name;
	private final Token access;

	public ClassDeclarationNode(Token name, Token access) {
		this.name = name;
		this.access = access;
	}

	@Override
	public void visit(FileContext context) throws SemanticException {

	}

	@Override
	public void preprocess(Context context) {
		ContextType prevType = context.getType();
		String prevClass = context.getCurrentClass();

		ClassWriter writer = initClass(context);

		createDefaultConstructor(writer);

		writer.visitEnd();

		context.setType(prevType);
		context.setCurrentClass(prevClass);
	}

	private ClassWriter initClass(Context context) {
		context.setType(ContextType.CLASS);
		//TODO Package name

		int accessLevel = getAccessLevel();

		String className = name.getValue();

		// "private" class
		if(accessLevel == 0) {
			className = context.getCurrentClass() + "$" + className;
			context.getImports().put(name.getValue(), className);
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
		return "class %s {}".formatted(name.getValue());
	}
}
