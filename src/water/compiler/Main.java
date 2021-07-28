package water.compiler;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import water.compiler.compiler.Context;
import water.compiler.compiler.Scope;
import water.compiler.compiler.SemanticException;
import water.compiler.lexer.Lexer;
import water.compiler.lexer.Token;
import water.compiler.parser.ASTPrettyPrinter;
import water.compiler.parser.Node;
import water.compiler.parser.Parser;
import water.compiler.parser.UnexpectedTokenException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Parses command line arguments and passes them correctly to the lexer, parser, and compiler.
 * Compiling is done in two stages, with the AST being cached between them:
 * preprocessing - build a 'template' of the class, with all fields and methods, but no implementations.
 * compiling - build a full class, including implementations, to be outputted.
 *
 * Preprocessing allows for full Class references to be built.
 */
@Parameters(separators = " |=")
public class Main {

	@Parameter(names = { "-h", "--help" }, description = "Shows a help page", help = true)
	private boolean help = false;

	@Parameter(names = { "-v", "--version" }, description = "Displays version information", help = true)
	private boolean version = false;

	@Parameter(names = { "-pp", "--prettyprint" }, description = "Pretty print generated AST - used for debugging the compiler")
	private boolean prettyPrint = false;

	@Parameter(names = { "-d", "--outputDir" }, description = "The directory where generated files are placed")
	private String outputDirectory = null;

	@Parameter(names = { "-o", "--optimize" }, description = "Set the optimization configuration path")
	private String optimizeConfig = null;

	@Parameter(
			names= { "-p", "-cp", "--classpath" },
			description = "The directories or jars to add to the classpath of the compiler",
			converter = PathConverter.class,
			splitter = PathSplitter.class)
	private List<Path> classpath = null;

	@Parameter(description = "Files to be compiled", required = true)
	private List<String> files = new ArrayList<>();

	private JCommander jCommander;

	public static void main(String[] args) {
		Main main = new Main();

		main.setJCommander(JCommander.newBuilder().addObject(main).build());

		main.getJCommander().setProgramName("water");

		try {
			main.getJCommander().parse(args);
		}
		catch (ParameterException e) {
			System.err.println("Invalid Parameters: " + e.getLocalizedMessage());
			e.usage();
			System.exit(1);
		}
		main.run();
	}

	private void run() {
		testInformationalParameters();

		Properties optimizations = getOptimizationConfiguration();

		List<Path> paths = files.stream().map(Path::of).collect(Collectors.toList());

		WaterClassLoader classPathLoader = WaterClassLoader.loadClasspath(classpath);

		WaterClassLoader loader = new WaterClassLoader(classPathLoader);

		ArrayList<FileContext> fileContexts = new ArrayList<>();

		// Lex, parse, and preprocess (build classes) for all files
		for(Path path : paths) {
			try {
				String source = Files.readString(path);

				Lexer lexer = new Lexer();
				List<Token> lexResult = lexer.lex(source);

				Parser parser = new Parser();
				Node program = parser.parse(lexResult);
				if(prettyPrint) System.out.println(ASTPrettyPrinter.prettyPrint(program));

				Context context = new Context();
				context.setSource(path.getFileName().toString());
				context.setLoader(loader);
				Scope redefinitionResolver = new Scope(context);
				context.setScope(redefinitionResolver);

				program.preprocess(context);

				byte[] klassRep = context.getClassWriter().toByteArray();
				Class<?> klass = loader.define(context.getClassName().replace('/', '.'), klassRep);
				fileContexts.add(new FileContext(program, context, klass, path, optimizations));
			} catch (IOException e) {
				error(2, "Failure reading file '%s': %s", path.toString(), e.getClass().getSimpleName().replace("Exception", ""));
			} catch (UnexpectedTokenException e) {
				error(-1, e.getErrorMessage(path.toString()));
			} catch (SemanticException e) {
				error(-2, e.getErrorMessage(path.toString()));
			}
		}

		// Compile for all classes (no re-parse)
		for(FileContext fc : fileContexts) {
			Scope scope = new Scope(fc);
			fc.getContext().setScope(scope);

			try {
				fc.getAst().visit(fc);
			} catch (SemanticException e) {
				error(-2, e.getErrorMessage(fc.getPath().toString()));
			}

			byte[] klassRep = fc.getContext().getClassWriter().toByteArray();

			String outputDir = outputDirectory == null ? fc.getPath().getParent().toString() : outputDirectory;
			String packageDir = fc.getContext().getPackageName();
			String className = fc.getPath().getFileName().toString().replaceAll("(?<!^)[.].*", ".class");

			Path classFile = Path.of(outputDir, packageDir, className);
			try {
				Files.createDirectories(classFile.getParent());
				Files.write(classFile, klassRep);
			} catch (IOException e) {
				error(3, "Failure writing file '%s': %s", classFile.toString(), e.getClass().getSimpleName().replace("Exception", ""));
			}
		}
	}

	private void error(int code, String format, Object... args) {
		System.err.printf(format + "\n", args);
		System.exit(code);
	}

	private void testInformationalParameters() {
		if(help) {
			jCommander.usage();
			System.exit(0);
		}
		if(version) {
			System.out.println("Water Compiler (JDK): " + VersionInformation.getVersionFormatted());
			System.exit(0);
		}
	}

	private Properties getOptimizationConfiguration() {
		Properties optimizations = null;

		if(optimizeConfig == null) {
			optimizations = OptimizationReader.getDefaults();
		}
		else {
			try {
				optimizations = OptimizationReader.readOptimizations(Path.of(optimizeConfig));
			} catch (IOException e) {
				System.err.printf("Failure reading file '%s': %s", optimizeConfig, e.getClass().getSimpleName().replace("Exception", ""));
				System.exit(2);
			}
		}
		return optimizations;
	}

	public void setJCommander(JCommander jCommander) {
		this.jCommander = jCommander;
	}

	public JCommander getJCommander() {
		return this.jCommander;
	}

}
