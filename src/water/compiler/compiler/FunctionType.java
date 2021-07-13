package water.compiler.compiler;

/**
 * The type of function:
 * sout allows the compiler to insert a 'System.out' access before the function - used for 'println' and related
 */
public enum FunctionType {
	STATIC,
	SOUT
}
