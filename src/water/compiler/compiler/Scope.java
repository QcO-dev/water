package water.compiler.compiler;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.parser.Node;
import water.compiler.util.Pair;
import water.compiler.util.TypeUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
		addFunction(new Function(FunctionType.SOUT, "println", "java/io/PrintStream", Type.getMethodType("(F)V")));
		addFunction(new Function(FunctionType.SOUT, "println", "java/io/PrintStream", Type.getMethodType("(I)V")));
		addFunction(new Function(FunctionType.SOUT, "println", "java/io/PrintStream", Type.getMethodType("(Z)V")));
		addFunction(new Function(FunctionType.SOUT, "println", "java/io/PrintStream", Type.getMethodType("(C)V")));
		addFunction(new Function(FunctionType.SOUT, "println", "java/io/PrintStream", Type.getMethodType("(J)V")));
		addFunction(new Function(FunctionType.SOUT, "println", "java/io/PrintStream", Type.getMethodType("(Ljava/lang/Object;)V")));

		updateCurrentClassMethods(context);
	}

	public Scope(Context context) {
		this.context = context;
		this.functionMap = new HashMap<>();
		this.variables = new HashMap<>();
	}

	private Scope() {}

	public void updateCurrentClassMethods(FileContext context) {
		Class<?> klass = context.getCurrentClass();
		if(klass == null) return;
		for(Method m : klass.getDeclaredMethods()) {
			int modifier = m.getModifiers();
			boolean isStatic = Modifier.isStatic(modifier);
			addFunction(new Function(isStatic ? FunctionType.STATIC : FunctionType.CLASS, m.getName(), Type.getInternalName(klass), Type.getType(m)));
		}

		for(Field f : klass.getDeclaredFields()) {
			int modifier = f.getModifiers();
			boolean isStatic = Modifier.isStatic(modifier);
			addVariable(new Variable(isStatic ? VariableType.STATIC : VariableType.CLASS, f.getName(), Type.getInternalName(klass), Type.getType(f.getType()), Modifier.isFinal(f.getModifiers())));
		}
	}

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

	public Function lookupFunction(String name, Type[] argsTypes, Node[] args, boolean visit, FileContext fc) throws ClassNotFoundException, SemanticException {
		ArrayList<Function> funcs = functionMap.get(name);
		if(funcs == null) return null;

		ArrayList<Pair<Integer, Function>> possible = new ArrayList<>();

		out : for(Function f : funcs) {
			Type[] expectArgs = f.getType().getArgumentTypes();

			if (expectArgs.length != argsTypes.length) continue;

			int changes = 0;

			for (int i = 0; i < expectArgs.length; i++) {
				Type expectArg = expectArgs[i];
				Type arg = argsTypes[i];

				if (arg.getSort() == Type.VOID)
					continue out;

				if (TypeUtil.isAssignableFrom(expectArg, arg, context, false)) {
					if (!expectArg.equals(arg)) changes++;
				} else {
					continue out;
				}
			}
			possible.add(new Pair<>(changes, f));
		}
		if(possible.size() == 0) return null;

		possible.sort(Comparator.comparingInt(Pair::getFirst));

		Function resolved = possible.get(0).getSecond();

		Type[] resolvedArgs = resolved.getType().getArgumentTypes();

		if(visit) {
			for (int i = 0; i < resolvedArgs.length; i++) {
				Type resolvedArg = resolvedArgs[i];
				Node arg = args[i];

				arg.visit(fc);
				TypeUtil.isAssignableFrom(resolvedArg, argsTypes[i], context, true);
			}
		}

		return resolved;
	}

	public Function exactLookupFunction(String name, Type... args) throws ClassNotFoundException {
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

	public Function lookupFunction(String name, Type... args) throws ClassNotFoundException {
		try {
			return lookupFunction(name, args, null, false, null);
		} catch (SemanticException e) {
			assert false; // Unreachable
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
