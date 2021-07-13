package water.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The class loader used by the compiler to resolve classes, including those which are defined in the compiling classes
 */
public class WaterClassLoader extends ClassLoader {

	public WaterClassLoader(ClassLoader parent) {
		super(parent);
	}

	/**
	 * Define a new class
	 * @param name The class name (fully qualified)
	 * @param b The byte[] representation of the class
	 * @return Class object representing the defined class
	 */
	public Class<?> define(String name, byte[] b) {
		return defineClass(name, b, 0, b.length);
	}

	public static WaterClassLoader loadClasspath(List<Path> classpath) {
		WaterClassLoader loader = new WaterClassLoader(ClassLoader.getSystemClassLoader());

		if(classpath == null) return loader;

		for(Path p : classpath) {
			if(Files.isDirectory(p)) {
				try(Stream<Path> stream = Files.walk(p, Integer.MAX_VALUE)) {

					List<Path> classes = stream
							.map(p::relativize)
							.filter(f -> f.toString().endsWith(".class"))
							.collect(Collectors.toUnmodifiableList());

					for(Path klass : classes) {
						StringBuilder className = new StringBuilder();

						for(int i = 0; i < klass.getNameCount(); i++) {
							className.append(klass.getName(i).toString()).append('.');
						}

						className.delete(className.length() - 7, className.length());

						Path abs = p.resolve(klass);

						byte[] classData = Files.readAllBytes(abs);

						loader.define(className.toString(), classData);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			else {
				//TODO JARS
				throw new UnsupportedOperationException("Expected directory");
			}
		}
		return loader;
	}

}
