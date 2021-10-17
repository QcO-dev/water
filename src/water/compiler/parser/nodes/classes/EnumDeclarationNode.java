package water.compiler.parser.nodes.classes;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Token;
import water.compiler.lexer.TokenType;
import water.compiler.parser.Node;
import water.compiler.util.TypeUtil;

import java.util.List;

public class EnumDeclarationNode implements Node {
	private final Token name;
	private final List<Token> fields;
	private final Token access;

	public EnumDeclarationNode(Token name, List<Token> fields, Token access) {
		this.name = name;
		this.fields = fields;
		this.access = access;
	}

	@Override
	public void visit(FileContext fc) throws SemanticException {
		Context context = fc.getContext();

		String prevClass = context.getCurrentClass();

		ClassWriter writer = initEnumClass(context);

		Type enumType = Type.getObjectType(context.getCurrentClass());

		MethodVisitor staticMethod = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		staticMethod.visitCode();

		context.setMethodVisitor(staticMethod);

		int count = 0;
		for(Token field : fields) {
			writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, field.getValue(), enumType.getDescriptor(), null, null);

			staticMethod.visitTypeInsn(Opcodes.NEW, enumType.getInternalName());
			staticMethod.visitInsn(Opcodes.DUP);
			staticMethod.visitLdcInsn(field.getValue());
			TypeUtil.generateCorrectInt(count, context);
			staticMethod.visitMethodInsn(Opcodes.INVOKESPECIAL, enumType.getInternalName(), "<init>", "(Ljava/lang/String;I)V", false);
			staticMethod.visitFieldInsn(Opcodes.PUTSTATIC, enumType.getInternalName(), field.getValue(), enumType.getDescriptor());
		}

		staticMethod.visitInsn(Opcodes.RETURN);
		staticMethod.visitMaxs(0, 0);
		staticMethod.visitEnd();

		// Build private constructor
		MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
		constructor.visitCode();
		constructor.visitVarInsn(Opcodes.ALOAD, 0);
		constructor.visitVarInsn(Opcodes.ALOAD, 1);
		constructor.visitVarInsn(Opcodes.ILOAD, 2);
		constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
		constructor.visitInsn(Opcodes.RETURN);
		constructor.visitMaxs(0, 0);
		constructor.visitEnd();

		writer.visitEnd();
		context.setCurrentClass(prevClass);
	}

	@Override
	public void preprocess(Context context) throws SemanticException {

		String prevClass = context.getCurrentClass();

		ClassWriter writer = initEnumClass(context);

		Type enumType = Type.getObjectType(context.getCurrentClass());

		// Build static method outline
		MethodVisitor staticMethod = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		staticMethod.visitCode();
		staticMethod.visitInsn(Opcodes.RETURN);
		staticMethod.visitMaxs(0, 0);
		staticMethod.visitEnd();

		// Build private constructor
		MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
		constructor.visitCode();
		// For bytecode checker
		constructor.visitVarInsn(Opcodes.ALOAD, 0);
		constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "()V", false);
		constructor.visitInsn(Opcodes.RETURN);
		constructor.visitMaxs(0, 0);
		constructor.visitEnd();

		// Fields

		for(Token field : fields) {
			writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, field.getValue(), enumType.getDescriptor(), null, null);
		}


		writer.visitEnd();
		context.setCurrentClass(prevClass);

	}

	@Override
	public boolean isNewClass() {
		return true;
	}

	@Override
	public void buildClasses(Context context) {
		String prevClass = context.getCurrentClass();

		ClassWriter writer = initEnumClass(context);
		writer.visitEnd();

		context.setCurrentClass(prevClass);
	}

	private ClassWriter initEnumClass(Context context) {
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

		writer.visit(Opcodes.V9, accessLevel | Opcodes.ACC_SUPER | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM, className, "Ljava/lang/Enum<L%s;>;".formatted(className), "java/lang/Enum", null);

		writer.visitSource(context.getSource(), null);

		context.getClassWriterMap().put(className, writer);

		return writer;
	}

	private int getAccessLevel() {
		if(access == null || access.getType() == TokenType.PUBLIC) return Opcodes.ACC_PUBLIC;
		return 0;
	}
}
