package xsynth;

public class SourceLocation {
	private final String filename;
	private final int lineno;

	public SourceLocation(final String filename, final int lineno) {
		this.filename = filename;
		this.lineno = lineno;
	}

	public String getFilename() {
		return filename;
	}

	public int getLineNumber() {
		return lineno;
	}

	@Override
	public String toString() {
		return filename + ":" + lineno;
	}
}
