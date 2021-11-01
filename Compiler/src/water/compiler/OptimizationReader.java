package water.compiler;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

public class OptimizationReader {

	/**
	 * Optimisations which are on by default.
	 */
	private static final String[] DEFAULTS = {
		"constant.string.concat",
		"constant.arithmetic",
		"constant.unary"
	};

	/**
	 * Given a path, resolve optimisation values, including defaults.
	 * @param path The path to the configuration file
	 * @return The Properties mapping of optimisations
	 * @throws IOException If an exception occurred reading the path
	 */
	public static Properties readOptimizations(Path path) throws IOException {
		Properties properties = new Properties();

		if(Boolean.parseBoolean((String) properties.getOrDefault("defaults", "true"))) {
			for (String optimization : DEFAULTS) {
				properties.put(optimization, true);
			}
		}

		properties.load(new FileInputStream(path.toFile()));

		for (Map.Entry<Object, Object> entry : properties.entrySet()) {
			Object key = entry.getKey();
			Object value = entry.getValue();

			boolean newValue = Boolean.parseBoolean(value.toString());

			properties.replace(key, newValue);
		}

		return properties;
	}

	/**
	 * Gets a default configuration for optimisations
	 *
	 * @return The default optimisations
	 */
	public static Properties getDefaults() {
		Properties properties = new Properties();
		for (String optimization : DEFAULTS) {
			properties.put(optimization, true);
		}
		return properties;
	}

}
