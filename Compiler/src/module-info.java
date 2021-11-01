module water.compiler {
	requires jcommander;
	requires org.objectweb.asm;
	opens water.compiler to jcommander;
}