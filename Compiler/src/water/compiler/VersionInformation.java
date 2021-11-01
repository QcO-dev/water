package water.compiler;

/**
 * Provides information about the compiler version
 */
public final class VersionInformation {
	public static int MAJOR = 0;
	public static int MINOR = 0;
	public static int PATCH = 1;

	public static String FORMAT = "v%d.%d.%d";

	public static String getVersionFormatted() {
		return FORMAT.formatted(MAJOR, MINOR, PATCH);
	}
}
