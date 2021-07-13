package water.compiler.compiler;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.util.TypeUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Represents the current scope of the compiler
 * Includes variables and functions, locals, etc.
 * Nested for each block.
 */
public class Scope {
	private FileContext fileContext;
	private Context context;
	private HashMap<String, ArrayList<Function>> functionMap;
	private HashMap<String, Variable> variables;
	private int localIndex;
	private Type returnType;
	private boolean returned;

	public Scope(FileContext context) {
		functionMap = new HashMap<>();
		variables = new HashMap<>();
		this.context = context.getContext();
		this.localIndex = 0;
		this.returnType = Type.VOID_TYPE;

		addFunction(new Function(FunctionType.SOUT, "println", "java/io/PrintStream", Type.getMethodType("()V")));
		addFunction(new Function(FunctionType.SOUT, "println", "java/io/PrintStream", Type.getMethodType("(D)V")));
		addFunction(new Function(FunctionType.SOUT, "println", "java/io/PrintStream", Type.getMethodType("(I)V")));
		addFunction(new Function(FunctionType.SOUT, "println", "java/io/PrintStream", Type.getMethodType("(Z)V")));
		addFunction(new Function(FunctionType.SOUT, "println", "java/io/PrintStream", Type.getMethodType("(Ljava/lang/Object;)V")));

		for(Method m : context.getKlass().getDeclaredMethods()) {
			addFunction(new Function(FunctionType.STATIC, m.getName(), Type.getInternalName(context.getKlass()), Type.getType(m)));
		}

		for(Field f : context.getKlass().getDeclaredFields()) {
			addVariable(new Variable(VariableType.GLOBAL, f.getName(), Type.getInternalName(context.getKlass()), Type.getType(f.getType())));
		}
	}

	public Scope(Context context) {
		this.context = context;
		this.functionMap = new HashMap<>();
		this.variables = new HashMap<>();
	}

	private Scope() {}

	public Scope nextDepth() {
		Scope scope = new Scope();
		scope.setFileContext(fileContext)
				.setContext(context)
				.setVariables(new HashMap<>(variables))
				.setFunctionMap(new HashMap<>(functionMap))
				.setReturnType(returnType)
				.setReturned(returned)
				.setLocalIndex(localIndex);
		return scope;
	}

	public ArrayList<Function> lookupFunctions(String name) {
		return functionMap.get(name);
	}

	public Function lookupFunction(String name, Type... args) throws ClassNotFoundException {
		ArrayList<Function> funcs = functionMap.get(name);
		if(funcs == null) return null;

		out : for(Function f : funcs) {
			Type[] expectArgs = f.getType().getArgumentTypes();

			if(expectArgs.length != args.length) continue;

			for(int i = 0; i < expectArgs.length; i++) {
				Type expectArg = expectArgs[i];
				Type arg = args[i];

				if(arg.getSort() == Type.VOID)
					continue out;

				if(!TypeUtil.typeToClass(expectArg, context).isAssignableFrom(TypeUtil.typeToClass(arg, context)))
					continue out;
			}
			return f;
		}
		return null;
	}

	public void addFunction(Function function) {
		String name = function.getName();
		if(functionMap.get(name) == null) functionMap.put(name, new ArrayList<>());

		functionMap.get(name).add(function);
	}

	public void addVariable(Variable variable) {
		String name = variable.getName();
		variables.put(name, variable);
	}

	public Variable lookupVariable(String name) {
		return variables.get(name);
	}

	public int nextLocal() {
		return localIndex++;
	}

	public void setLocalIndex(int localIndex) {
		this.localIndex = localIndex;
	}

	private Scope setFileContext(FileContext fileContext) {
		this.fileContext = fileContext;
		return this;
	}

	private Scope setContext(Context context) {
		this.context = context;
		return this;
	}

	private Scope setFunctionMap(HashMap<String, ArrayList<Function>> functionMap) {
		this.functionMap = functionMap;
		return this;
	}

	private Scope setVariables(HashMap<String, Variable> variables) {
		this.variables = variables;
		return this;
	}

	public Type getReturnType() {
		return returnType;
	}

	public Scope setReturnType(Type returnType) {
		this.returnType = returnType;
		return this;
	}

	public boolean isReturned() {
		return returned;
	}

	public Scope setReturned(boolean returned) {
		this.returned = returned;
		return this;
	}
}
