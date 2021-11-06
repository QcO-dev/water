package water.compiler.util;

import org.objectweb.asm.ClassWriter;

public class WaterClassWriter extends ClassWriter {

	private ClassLoader loader;

	public WaterClassWriter(int flags, ClassLoader loader) {
		super(flags);
		this.loader = loader == null ? ClassLoader.getSystemClassLoader() : loader;
	}

	@Override
	protected ClassLoader getClassLoader() {
		return loader;
	}
}
