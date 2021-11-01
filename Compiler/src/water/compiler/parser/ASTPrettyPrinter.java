package water.compiler.parser;

import java.util.ArrayList;
import java.util.stream.Collectors;

/** Static helper class */
public class ASTPrettyPrinter {
	/**
	 * Produces a prettified string from a program's toString method.
	 * toString() is expected to produce something such as
	 * <code>function x() { var x = 12; println(x); }</code>
	 *
	 * which would be transformed by inserting newlines and indentation.
	 *
	 * @param program The root of the AST of the program
	 * @return A prettified representation
	 */
	public static String prettyPrint(Node program) {
		String repr = program.toString();

		// Step 1: Add line breaks
		String linesAdded = repr.replace(";", ";\n").replace("{", "{\n").replace("}", "}\n");

		// Step 2: Add indentation
		int scopeLevel = 0;

		String[] lines = linesAdded.split("\n");
		ArrayList<String> newLines = new ArrayList<>();

		for(String line : lines) {
			if(line.endsWith("}")) scopeLevel--;
			newLines.add("\t".repeat(scopeLevel) + line);
			if(line.endsWith("{")) scopeLevel++;
		}

		return newLines.stream().collect(Collectors.joining("\n"));
	}

}
