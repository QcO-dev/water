package water.compiler.compiler;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import water.compiler.WaterClassLoader;

import java.util.HashMap;

/**
 * Provides information to the compiler about its current state
 * and different objects which should be available to different nodes
 */
public class Context {
	private final HashMap<String, String> imports;
	private ContextType type;
	private String source;
	private String className;
	private String packageName;
	private ClassWriter classWriter;
	private MethodVisitor methodVisitor;
	private MethodVisitor staticMethodVisitor;
	private WaterClassLoader loader;
	private Scope scope;
	private int currentLine;

	public Context() {
		this.imports = new HashMap<>();
	}

	public ContextType getType() {
		return type;
	}

	public void setType(ContextType type) {
		this.type = type;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getPackageName() {
		return packageName;
	}

	public Context setPackageName(String packageName) {
		this.packageName = packageName;
		return this;
	}

	public ClassWriter getClassWriter() {
		return classWriter;
	}

	public void setClassWriter(ClassWriter classWriter) {
		this.classWriter = classWriter;
	}

	public MethodVisitor getMethodVisitor() {
		return methodVisitor;
	}

	public void setMethodVisitor(MethodVisitor methodVisitor) {
		this.methodVisitor = methodVisitor;
	}

	public MethodVisitor getStaticMethodVisitor() {
		return staticMethodVisitor;
	}

	public void setStaticMethodVisitor(MethodVisitor staticMethodVisitor) {
		this.staticMethodVisitor = staticMethodVisitor;
	}

	public WaterClassLoader getLoader() {
		return loader;
	}

	public void setLoader(WaterClassLoader loader) {
		this.loader = loader;
	}

	public Scope getScope() {
		return scope;
	}

	public void setScope(Scope scope) {
		this.scope = scope;
	}

	public HashMap<String, String> getImports() {
		return imports;
	}

	public void updateLine(int line) {
		if(currentLine == line) return;
		currentLine = line;
		Label l = new Label();
		MethodVisitor mv = type == ContextType.GLOBAL ? getStaticMethodVisitor() : getMethodVisitor();
		mv.visitLabel(l);
		mv.visitLineNumber(line, l);
	}
}
