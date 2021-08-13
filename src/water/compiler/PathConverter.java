package water.compiler;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;

import java.nio.file.Files;
import java.nio.file.Path;

public class PathConverter implements IStringConverter<Path> {
	@Override
	public Path convert(String s) {
		Path path = Path.of(s);

		if(Files.notExists(path)) {
			throw new ParameterException("Path '%s' does not exist".formatted(path.toString()));
		}
		return path;
	}
}
