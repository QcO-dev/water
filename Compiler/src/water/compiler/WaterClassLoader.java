package water.compiler;

import water.compiler.util.Unthrow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The class loader used by the compiler to resolve classes, including those which are defined in the compiling classes
 */
public class WaterClassLoader extends URLClassLoader {

	public WaterClassLoader(List<Path> classpath, ClassLoader parent) {
		super(classpath == null ? new URL[0] : classpath.stream().filter(p -> !Files.isDirectory(p)).map(p -> Unthrow.wrap(() -> p.toFile().toURI().toURL())).toArray(URL[]::new), parent);
	}

	public WaterClassLoader(ClassLoader parent) {
		super(new URL[0], parent);
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

	public static WaterClassLoader loadClasspath(List<Path> classpath) throws IOException {
		WaterClassLoader loader = new WaterClassLoader(classpath, ClassLoader.getSystemClassLoader());

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
				}
			}
		}
		return loader;
	}

}
