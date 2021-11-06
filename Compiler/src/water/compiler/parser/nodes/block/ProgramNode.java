package water.compiler.parser.nodes.block;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.ContextType;
import water.compiler.compiler.SemanticException;
import water.compiler.parser.Node;
import water.compiler.parser.nodes.classes.ClassDeclarationNode;
import water.compiler.parser.nodes.variable.VariableDeclarationNode;
import water.compiler.util.WaterClassWriter;

import java.util.List;
import java.util.stream.Collectors;

public class ProgramNode implements Node {
	private final List<Node> declarations;
	private final List<Node> imports;
	private final Node packageName;
	/** This variable is true when a top level variable is present. Its use is to avoid unnecessary code generation. */
	private boolean staticVariableInit = false;
	/** Records if this program needs to generate a Wtr.class file, or if it only defines other classes */
	// This behaviour is what causes the strange looking if statements, where different code only needs to happen for a standalone class.
	private boolean standaloneClass = false;
	
	public ProgramNode(Node packageName, List<Node> imports, List<Node> declarations) {
		this.packageName = packageName;
		this.imports = imports;
		this.declarations = declarations;
	}

	@Override
	public void buildClasses(Context context) throws SemanticException {
		String packageN = packageName == null ? "" : packageName.getReturnType(context).getInternalName();
		context.setPackageName(packageN);

		String source = context.getSource();
		String name = source.substring(0, source.indexOf(".")) + "Wtr";

		standaloneClass = declarations.stream().filter(n -> !n.isNewClass()).toArray().length != 0;

		ClassWriter writer = null;

		if(standaloneClass) {
			writer = initClass(name, context);
		}

		for(Node n : declarations) {
			n.buildClasses(context);
		}

		if(standaloneClass) {
			writer.visitEnd();
		}
	}

	@Override
	public void preprocess(Context context) throws SemanticException {
		String packageN = packageName == null ? "" : packageName.getReturnType(context).getInternalName();
		context.setPackageName(packageN);

		String source = context.getSource();
		String name = source.substring(0, source.indexOf(".")) + "Wtr";

		standaloneClass = declarations.stream().filter(n -> !n.isNewClass()).toArray().length != 0;

		ClassWriter writer = null;

		if(standaloneClass) {
			writer = initClass(name, context);
		}

		for(Node n : imports) n.preprocess(context);

		for(Node n : declarations) {
			n.preprocess(context);
			if(n instanceof VariableDeclarationNode) staticVariableInit = true;
		}

		if(standaloneClass) {
			if(staticVariableInit) {
				MethodVisitor staticMethod = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
				staticMethod.visitCode();
				staticMethod.visitInsn(Opcodes.RETURN);
				staticMethod.visitMaxs(0, 0);
				staticMethod.visitEnd();
			}

			writer.visitEnd();
		}
	}

	@Override
	public void visit(FileContext context) throws SemanticException {
		ClassWriter writer = null;
		MethodVisitor staticMethod = null;
		if(standaloneClass) {
			String source = context.getContext().getSource();
			String name = source.substring(0, source.indexOf(".")) + "Wtr";

			writer = initClass(name, context.getContext());

			if(staticVariableInit) {
				staticMethod = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
				context.getContext().setStaticMethodVisitor(staticMethod);

				staticMethod.visitCode();
			}
		}

		for(Node n : declarations) {
			n.visit(context);
		}

		if(standaloneClass) {
			if(staticVariableInit) {
				staticMethod.visitInsn(Opcodes.RETURN);
				staticMethod.visitMaxs(0, 0);
				staticMethod.visitEnd();
			}

			writer.visitEnd();
		}
	}

	private ClassWriter initClass(String name, Context context) {
		context.setType(ContextType.GLOBAL);

		String source = context.getSource();

		if(packageName != null) name = context.getPackageName() + "/" + name;

		context.setCurrentClass(name);

		ClassWriter writer = new WaterClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES, context.getLoader());

		writer.visit(Opcodes.V9, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);

		writer.visitSource(source, null);

		context.getClassWriterMap().put(name, writer);

		return writer;
	}

	@Override
	public String toString() {
		return (packageName == null ? "" : packageName.toString())
				+ imports.stream().map(Node::toString).collect(Collectors.joining())
				+ declarations.stream().map(Node::toString).collect(Collectors.joining());
	}
}
