package water.compiler;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
