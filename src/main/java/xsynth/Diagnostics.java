package xsynth;

import java.util.List;

public class Diagnostics {
	protected void print(final String level, final SourceLocation sloc, final List<String> line, final String message) {
		System.err.println(sloc + ": " + level + " " + message);
		if (line != null)
			System.err.println("\t" + String.join(" ", line));
	}

	/**
	 * Reports an unusual but acceptable condition, or just plain information
	 * output.
	 */
	public void info(final SourceLocation sloc, final List<String> line, final String message) {
		print("INFO", sloc, line, message);
	}

	/**
	 * Reports an unusual condition that isn't fatal, but likely not what the user
	 * intended.
	 */
	public void warn(final SourceLocation sloc, final List<String> line, final String message) {
		print("WARNING", sloc, line, message);
	}

	/**
	 * Reports a fatal condition that prevents further processing.
	 *
	 * @return AbortedException (for throwing)
	 */
	public AbortedException error(final SourceLocation sloc, final List<String> line, final String message)
			throws AbortedException {
		print("ERROR", sloc, line, message);
		return new AbortedException();
	}

	/**
	 * Thrown by {@link #error(SourceLocation, List, String)}, ie. indicates that
	 * processing is being aborted because of an already-reported error. It
	 * therefore doesn't need to be reported.
	 */
	@SuppressWarnings("serial")
	public static class AbortedException extends Exception {
		private AbortedException() {
			super("aborted due to error() call");
		}
	}
}
