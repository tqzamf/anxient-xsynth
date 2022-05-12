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

public class BlifReader implements AutoCloseable {
	private final BufferedReader in;
	private int nextPhysLine;
	private int logLine;

	public BlifReader(final String filename) throws FileNotFoundException {
		this(new FileInputStream(filename));
	}

	public BlifReader(final InputStream in) {
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

	public List<String> nextLine() throws IOException {
		final StringBuilder buffer = new StringBuilder();
		while (true) {
			logLine = nextPhysLine;
			buffer.setLength(0);

			while (true) {
				final String line = readLine();
				if (line == null)
					if (buffer.length() == 0)
						return null;
					else
						throw new IllegalArgumentException("unexpected EOF");

				final int comment = line.indexOf('#');
				if (comment >= 0) {
					buffer.append(line.substring(0, comment));
					break; // cannot continue a comment line
				} else if (line.isEmpty() || line.charAt(line.length() - 1) != '\\') {
					buffer.append(line);
					break; // line could be continued but isn't
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

	public int getLineNumber() {
		return logLine;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}
