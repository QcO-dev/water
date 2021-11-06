module water.compiler {
	requires jcommander;
	requires org.objectweb.asm;
	requires water.runtime;
	opens water.compiler to jcommander;
}