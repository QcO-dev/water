package water.compiler.parser;

import org.objectweb.asm.Type;
import water.compiler.FileContext;
import water.compiler.compiler.Context;
import water.compiler.compiler.SemanticException;
import water.compiler.util.WaterType;

/**
 * All Nodes created by the Parser in the AST must implement this interface
 */
public interface Node {
	/** Generate bytecode / implementation of this node */
	void visit(FileContext context) throws SemanticException;
	/** Create outlining for template class */
	default void preprocess(Context context) throws SemanticException { }
	/** The type which this node will produce */
	default WaterType getReturnType(Context context) throws SemanticException { return WaterType.VOID_TYPE; }
	/** Returns a constant value for use in optimisations. Only needs to be implemented if isConstant can return true */
	default Object getConstantValue(Context context) throws SemanticException { return null; }
	/** If the node can be transformed to a constant value for optimisation */
	default boolean isConstant(Context context) throws SemanticException { return false; }
	/** Get the type of LValue */
	default LValue getLValue() { return LValue.NONE; }
	/** Get data needed to assign to LValue */
	default Object[] getLValueData() { return new Object[0]; }
	/** If the Node produces a new class file */
	default boolean isNewClass() { return false; }
	/** Create classes definitions */
	default void buildClasses(Context context) throws SemanticException {}
}
