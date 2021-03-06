package water.compiler;

import water.compiler.compiler.Context;
import water.compiler.parser.Node;

import java.nio.file.Path;
import java.util.Map;
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
	private Map<String, Class<?>> classMap;
	private final Path path;
	private final Properties optimizations;

	public FileContext(Node ast, Context context, Map<String, Class<?>> classMap, Path path, Properties optimizations) {
		this.ast = ast;
		this.context = context;
		this.classMap = classMap;
		this.path = path;
		this.optimizations = optimizations;
	}

	public Node getAst() {
		return ast;
	}

	public Context getContext() {
		return context;
	}

	public Map<String, Class<?>> getClassMap() {
		return classMap;
	}

	public void setClassMap(Map<String, Class<?>> classMap) {
		this.classMap = classMap;
	}

	public Path getPath() {
		return path;
	}

	public Class<?> getCurrentClass() {
		return classMap.get(context.getCurrentClass());
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
