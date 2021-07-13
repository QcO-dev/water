package water.compiler.parser.nodes.block;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.ContextType;
import water.compiler.compiler.SemanticException;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.variable.VariableDeclarationNode;

import java.util.List;
import java.util.stream.Collectors;

public class ProgramNode implements Node {
	private final List<Node> declarations;
	/** This variable is true when a top level variable is present. Its use is to avoid unnecessary code generation. */
	private boolean staticVariableInit = false;
	
	public ProgramNode(List<Node> declarations) {
		this.declarations = declarations;
	}

	@Override
	public String toString() {
		return declarations.stream().map(Node::toString).collect(Collectors.joining());
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		ClassWriter writer = initClass(context);
		
		for(Node n : declarations) {
			n.preprocess(context);
			if(n instanceof VariableDeclarationNode) staticVariableInit = true;
		}
		
		if(staticVariableInit) {
			MethodVisitor staticMethod = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			staticMethod.visitCode();
			staticMethod.visitInsn(Opcodes.RETURN);
			staticMethod.visitMaxs(0, 0);
			staticMethod.visitEnd();
		}

		writer.visitEnd();
	}

	@Override
	public void visit(FileContext context) throws SemanticException {

		ClassWriter writer = initClass(context.getContext());

		MethodVisitor staticMethod = null;
		if(staticVariableInit) {
			staticMethod = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
			context.getContext().setStaticMethodVisitor(staticMethod);

			staticMethod.visitCode();
		}

		for(Node n : declarations) {
			n.visit(context);
		}
		
		if(staticVariableInit) {
			staticMethod.visitInsn(Opcodes.RETURN);
			staticMethod.visitMaxs(0, 0);
			staticMethod.visitEnd();
		}

		writer.visitEnd();
	}

	private ClassWriter initClass(Context context) {
		context.setType(ContextType.GLOBAL);

		String source = context.getSource();
		String name = source.substring(0, source.indexOf("."));

		context.setClassName(name);

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

		writer.visit(Opcodes.V9, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);

		writer.visitSource(source, null);

		context.setClassWriter(writer);

		return writer;
	}
}
