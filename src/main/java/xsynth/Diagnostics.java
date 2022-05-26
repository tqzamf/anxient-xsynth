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
	 * output. Use when there's something syntactically wrong with the line.
	 */
	public void info(final SourceLocation sloc, final List<String> line, final String message) {
		print("INFO", sloc, line, message);
	}

	/**
	 * Reports an unusual but acceptable condition, or just plain information
	 * output. Use when there's something wrong with the position or semantics of
	 * the construct.
	 */
	public void info(final SourceLocation sloc, final String message) {
		info(sloc, null, message);
	}

	/**
	 * Reports an unusual condition that isn't fatal, but likely not what the user
	 * intended. Use when there's something syntactically wrong with the line.
	 */
	public void warn(final SourceLocation sloc, final List<String> line, final String message) {
		print("WARNING", sloc, line, message);
	}

	/**
	 * Reports an unusual condition that isn't fatal, but likely not what the user
	 * intended. Use when there's something wrong with the position or semantics of
	 * the construct.
	 */
	public void warn(final SourceLocation sloc, final String message) {
		warn(sloc, null, message);
	}

	/**
	 * Reports a fatal condition that prevents further processing. Use when there's
	 * something syntactically wrong with the line.
	 *
	 * @return AbortedException (for throwing)
	 */
	public AbortedException error(final SourceLocation sloc, final List<String> line, final String message) {
		print("ERROR", sloc, line, message);
		return new AbortedException(sloc, message);
	}

	/**
	 * Reports a fatal condition that prevents further processing. Use when there's
	 * something wrong with the position or semantics of the construct.
	 *
	 * @return AbortedException (for throwing)
	 */
	public AbortedException error(final SourceLocation sloc, final String message) {
		return error(sloc, null, message);
	}

	/**
	 * Thrown by {@link #error(SourceLocation, List, String)}, ie. indicates that
	 * processing is being aborted because of an already-reported error. It
	 * therefore doesn't need to be reported.
	 */
	@SuppressWarnings("serial")
	public static class AbortedException extends Exception {
		private AbortedException(final SourceLocation sloc, final String message) {
			super("aborted at " + sloc + ": " + message);
		}
	}
}
