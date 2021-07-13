package water.compiler.compiler;

/**
 * What the compiler is currently compiling
 * - the top level declarations, within a function, or within a class
 */
public enum ContextType {
	GLOBAL,
	CLASS,
	FUNCTION
}
