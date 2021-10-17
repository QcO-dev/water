package water.compiler.compiler;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import water.compiler.WaterClassLoader;
import water.compiler.parser.nodes.classes.ConstructorDeclarationNode;
import water.compiler.parser.nodes.variable.VariableDeclarationNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides information to the compiler about its current state
 * and different objects which should be available to different nodes
 */
public class Context {
	private final HashMap<String, String> imports;
	private final Map<String, ClassWriter> classWriterMap;
	private ContextType type;
	private String source;
	private String packageName;
	private String currentClass;
	private MethodVisitor methodVisitor;
	private MethodVisitor staticMethodVisitor;
	private MethodVisitor defaultConstructor;
	private WaterClassLoader loader;
	private Scope scope;
	private Type currentSuperClass;
	private boolean isStaticMethod;
	private boolean isConstructor;
	private int currentLine;
	private List<VariableDeclarationNode> classVariables;

	public Context() {
		this.imports = new HashMap<>();
		this.classWriterMap = new HashMap<>();
		initImports();
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

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getCurrentClass() {
		return currentClass;
	}

	public void setCurrentClass(String currentClass) {
		this.currentClass = currentClass;
	}

	public Map<String, ClassWriter> getClassWriterMap() {
		return classWriterMap;
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

	public MethodVisitor getDefaultConstructor() {
		return defaultConstructor;
	}

	public void setDefaultConstructor(MethodVisitor defaultConstructor) {
		this.defaultConstructor = defaultConstructor;
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

	public Type getCurrentSuperClass() {
		return currentSuperClass;
	}

	public void setCurrentSuperClass(Type currentSuperClass) {
		this.currentSuperClass = currentSuperClass;
	}

	public boolean isStaticMethod() {
		return isStaticMethod;
	}

	public void setStaticMethod(boolean staticMethod) {
		isStaticMethod = staticMethod;
	}

	public boolean isConstructor() {
		return isConstructor;
	}

	public void setConstructor(boolean constructor) {
		isConstructor = constructor;
	}

	public List<VariableDeclarationNode> getClassVariables() {
		return classVariables;
	}

	public void setClassVariables(List<VariableDeclarationNode> classVariables) {
		this.classVariables = classVariables;
	}

	public HashMap<String, String> getImports() {
		return imports;
	}

	public void updateLine(int line) {
		if(currentLine == line) return;
		currentLine = line;
		Label l = new Label();
		MethodVisitor mv = type == ContextType.GLOBAL ? getStaticMethodVisitor() : getMethodVisitor();
		if(mv == null) mv = getDefaultConstructor();
		mv.visitLabel(l);
		mv.visitLineNumber(line, l);
	}

	public ClassWriter getCurrentClassWriter() {
		return classWriterMap.get(currentClass);
	}

	private void initImports() {
		imports.put("String", "java.lang.String");
		imports.put("Object", "java.lang.Object");
		imports.put("Exception", "java.lang.Exception");
		imports.put("Throwable", "java.lang.Throwable");
	}
}
