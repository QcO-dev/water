package water.compiler.compiler;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import water.compiler.WaterClassLoader;
import water.compiler.parser.nodes.classes.ConstructorDeclarationNode;
import water.compiler.parser.nodes.variable.VariableDeclarationNode;
import water.compiler.util.WaterType;

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
	private WaterType currentSuperClass;
	private boolean isStaticMethod;
	private boolean isConstructor;
	private int currentLine;
	private List<VariableDeclarationNode> classVariables;
	private Label nullJumpLabel;

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

	public WaterType getCurrentSuperClass() {
		return currentSuperClass;
	}

	public void setCurrentSuperClass(WaterType currentSuperClass) {
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

	public Label getNullJumpLabel(Label other) {
		return nullJumpLabel == null ? other : nullJumpLabel;
	}

	public void setNullJumpLabel(Label nullJumpLabel) {
		this.nullJumpLabel = nullJumpLabel;
	}

	private void initImports() {
		imports.put("Appendable", "java.lang.Appendable");
		imports.put("AutoCloseable", "java.lang.AutoCloseable");
		imports.put("CharSequence", "java.lang.CharSequence");
		imports.put("Cloneable", "java.lang.Cloneable");
		imports.put("Comparable", "java.lang.Comparable");
		imports.put("Iterable", "java.lang.Iterable");
		imports.put("Readable", "java.lang.Readable");
		imports.put("Runnable", "java.lang.Runnable");
		imports.put("Boolean", "java.lang.Boolean");
		imports.put("Byte", "java.lang.Byte");
		imports.put("Character", "java.lang.Character");
		imports.put("Class", "java.lang.Class");
		imports.put("ClassLoader", "java.lang.ClassLoader");
		imports.put("ClassValue", "java.lang.ClassValue");
		imports.put("Compiler", "java.lang.Compiler");
		imports.put("Double", "java.lang.Double");
		imports.put("Enum", "java.lang.Enum");
		imports.put("Float", "java.lang.Float");
		imports.put("InheritableThreadLocal", "java.lang.InheritableThreadLocal");
		imports.put("Integer", "java.lang.Integer");
		imports.put("Long", "java.lang.Long");
		imports.put("Math", "java.lang.Math");
		imports.put("Number", "java.lang.Number");
		imports.put("Object", "java.lang.Object");
		imports.put("Package", "java.lang.Package");
		imports.put("Process", "java.lang.Process");
		imports.put("ProcessBuilder", "java.lang.ProcessBuilder");
		imports.put("Runtime", "java.lang.Runtime");
		imports.put("RuntimePermission", "java.lang.RuntimePermission");
		imports.put("SecurityManager", "java.lang.SecurityManager");
		imports.put("Short", "java.lang.Short");
		imports.put("StackTraceElement", "java.lang.StackTraceElement");
		imports.put("StrictMath", "java.lang.StrictMath");
		imports.put("String", "java.lang.String");
		imports.put("StringBuffer", "java.lang.StringBuffer");
		imports.put("StringBuilder", "java.lang.StringBuilder");
		imports.put("System", "java.lang.System");
		imports.put("Thread", "java.lang.Thread");
		imports.put("ThreadGroup", "java.lang.ThreadGroup");
		imports.put("ThreadLocal", "java.lang.ThreadLocal");
		imports.put("Throwable", "java.lang.Throwable");
		imports.put("Void", "java.lang.Void");
		imports.put("ArithmeticException", "java.lang.ArithmeticException");
		imports.put("ArrayIndexOutOfBoundsException", "java.lang.ArrayIndexOutOfBoundsException");
		imports.put("ArrayStoreException", "java.lang.ArrayStoreException");
		imports.put("ClassCastException", "java.lang.ClassCastException");
		imports.put("ClassNotFoundException", "java.lang.ClassNotFoundException");
		imports.put("CloneNotSupportedException", "java.lang.CloneNotSupportedException");
		imports.put("EnumConstantNotPresentException", "java.lang.EnumConstantNotPresentException");
		imports.put("Exception", "java.lang.Exception");
		imports.put("IllegalAccessException", "java.lang.IllegalAccessException");
		imports.put("IllegalArgumentException", "java.lang.IllegalArgumentException");
		imports.put("IllegalMonitorStateException", "java.lang.IllegalMonitorStateException");
		imports.put("IllegalStateException", "java.lang.IllegalStateException");
		imports.put("IllegalThreadStateException", "java.lang.IllegalThreadStateException");
		imports.put("IndexOutOfBoundsException", "java.lang.IndexOutOfBoundsException");
		imports.put("InstantiationException", "java.lang.InstantiationException");
		imports.put("InterruptedException", "java.lang.InterruptedException");
		imports.put("NegativeArraySizeException", "java.lang.NegativeArraySizeException");
		imports.put("NoSuchFieldException", "java.lang.NoSuchFieldException");
		imports.put("NoSuchMethodException", "java.lang.NoSuchMethodException");
		imports.put("NullPointerException", "java.lang.NullPointerException");
		imports.put("NumberFormatException", "java.lang.NumberFormatException");
		imports.put("ReflectiveOperationException", "java.lang.ReflectiveOperationException");
		imports.put("RuntimeException", "java.lang.RuntimeException");
		imports.put("SecurityException", "java.lang.SecurityException");
		imports.put("StringIndexOutOfBoundsException", "java.lang.StringIndexOutOfBoundsException");
		imports.put("TypeNotPresentException", "java.lang.TypeNotPresentException");
		imports.put("UnsupportedOperationException", "java.lang.UnsupportedOperationException");
		imports.put("AbstractMethodError", "java.lang.AbstractMethodError");
		imports.put("AssertionError", "java.lang.AssertionError");
		imports.put("BootstrapMethodError", "java.lang.BootstrapMethodError");
		imports.put("ClassCircularityError", "java.lang.ClassCircularityError");
		imports.put("ClassFormatError", "java.lang.ClassFormatError");
		imports.put("Error", "java.lang.Error");
		imports.put("ExceptionInInitializerError", "java.lang.ExceptionInInitializerError");
		imports.put("IllegalAccessError", "java.lang.IllegalAccessError");
		imports.put("IncompatibleClassChangeError", "java.lang.IncompatibleClassChangeError");
		imports.put("InstantiationError", "java.lang.InstantiationError");
		imports.put("InternalError", "java.lang.InternalError");
		imports.put("LinkageError", "java.lang.LinkageError");
		imports.put("NoClassDefFoundError", "java.lang.NoClassDefFoundError");
		imports.put("NoSuchFieldError", "java.lang.NoSuchFieldError");
		imports.put("NoSuchMethodError", "java.lang.NoSuchMethodError");
		imports.put("OutOfMemoryError", "java.lang.OutOfMemoryError");
		imports.put("StackOverflowError", "java.lang.StackOverflowError");
		imports.put("ThreadDeath", "java.lang.ThreadDeath");
		imports.put("UnknownError", "java.lang.UnknownError");
		imports.put("UnsatisfiedLinkError", "java.lang.UnsatisfiedLinkError");
		imports.put("UnsupportedClassVersionError", "java.lang.UnsupportedClassVersionError");
		imports.put("VerifyError", "java.lang.VerifyError");
		imports.put("VirtualMachineError", "java.lang.VirtualMachineError");
		imports.put("Deprecated", "java.lang.Deprecated");
		imports.put("Override", "java.lang.Override");
		imports.put("SafeVarargs", "java.lang.SafeVarargs");
		imports.put("SuppressWarnings", "java.lang.SuppressWarnings");
	}
}
