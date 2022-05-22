package xsynth.blif;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.SourceLocation;

public class BlifReader implements AutoCloseable {
	private final BufferedReader in;
	private final String filename;
	private int nextPhysLine;
	private int logLine;
	private final Diagnostics diag;

	public BlifReader(final Diagnostics diag, final String filename) throws FileNotFoundException {
		this(diag, new FileInputStream(filename), filename);
	}

	public BlifReader(final Diagnostics diag, final InputStream in, final String filename) {
		this.diag = diag;
		this.filename = filename;
		this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		nextPhysLine = 1;
	}

	private String readLine() throws IOException {
		final String line = in.readLine();
		if (line == null)
			return null;
		nextPhysLine++;
		return line;
	}

	public List<String> nextLine() throws IOException, AbortedException {
		final StringBuilder buffer = new StringBuilder();
		while (true) {
			logLine = nextPhysLine;
			buffer.setLength(0);

			while (true) {
				final String line = readLine();
				if (line == null) {
					if (buffer.length() != 0)
						throw diag.error(getCurrentLocation(), null, "line continued beyond end of file");
					return null;
				}

				final int comment = line.indexOf('#');
				if (comment >= 0) {
					if (line.charAt(line.length() - 1) == '\\' // .foo # comment \\
							|| comment > 0 && line.charAt(comment - 1) == '\\') // .foo \\# comment
						diag.warn(getCurrentLocation(), null, "cannot continue a comment line");
					buffer.append(line.substring(0, comment));
					break; // cannot continue a comment line
				} else if (!line.endsWith("\\")) {
					buffer.append(line);
					break; // logical line ends here
				} else // line ends with backslash, ie. continued line
					buffer.append(line.substring(0, line.length() - 1));
			}

			final Matcher m = Pattern.compile("\\s+|$").matcher(buffer);
			final List<String> fields = new ArrayList<>();
			int start = 0;
			while (m.find()) {
				if (m.start() - start > 0)
					fields.add(buffer.substring(start, m.start()));
				start = m.end();
			}

			if (!fields.isEmpty())
				return fields;
		}
	}

	/**
	 * @return {@link SourceLocation} of the last line returned by
	 *         {@link #nextLine()}
	 */
	public SourceLocation getCurrentLocation() {
		return new SourceLocation(filename, logLine);
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}
