package water.compiler;

import com.beust.jcommander.converters.IParameterSplitter;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class PathSplitter implements IParameterSplitter {
	@Override
	public List<String> split(String s) {
		return Arrays.asList(s.split(File.pathSeparator));
	}
}
