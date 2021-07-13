package water.compiler;

import water.compiler.compiler.Context;
import water.compiler.parser.Node;

import java.nio.file.Path;
import java.util.Properties;

/**
 *
 * Represents the context of an individual Water source file.
 *
 * @see water.compiler.compiler.Context
 */
public class FileContext {
	private final Node ast;
	private final Context context;
	private final Class<?> klass;
	private final Path path;
	private final Properties optimizations;

	public FileContext(Node ast, Context context, Class<?> klass, Path path, Properties optimizations) {
		this.ast = ast;
		this.context = context;
		this.klass = klass;
		this.path = path;
		this.optimizations = optimizations;
	}

	public Node getAst() {
		return ast;
	}

	public Context getContext() {
		return context;
	}

	public Class<?> getKlass() {
		return klass;
	}

	public Path getPath() {
		return path;
	}

	/**
	 *
	 * Based on the optimisations passed, resolve if this optimisation path is set to true,
	 * with a default of false if it is not set (including by the defaults).
	 *
	 * @param optimization The optimisation to resolve
	 * @return If the optimisation is true
	 */
	public boolean shouldOptimize(String optimization) {
		return (boolean) optimizations.getOrDefault(optimization, false);
	}
}
