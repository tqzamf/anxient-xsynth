package xsynth.blif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import xsynth.Diagnostics.AbortedException;
import xsynth.DiagnosticsShim;
import xsynth.SourceLocation;

public class BlifReaderTest {
	private DiagnosticsShim diag;
	private BlifReader reader;

	@Test
	public void testEmptyFile() throws IOException, AbortedException {
		createReader("");
		// null at EOF. calling again gives null again
		assertNull(reader.nextLine());
		assertNull(reader.nextLine());
		diag.assertNoMessages();
	}

	@Test
	public void testEndOfLine() throws IOException, AbortedException {
		// all 4 EOL common conditions: CR, LF, CRLF, and EOF
		createReader("a\nb\rc\r\nd");
		assertNextLine(1, "a");
		assertNextLine(2, "b");
		assertNextLine(3, "c");
		assertNextLine(4, "d");
		assertNull(reader.nextLine());
		diag.assertNoMessages();
	}

	@Test
	public void testSimpleModel() throws IOException, AbortedException {
		createReader(".model simple", // 1
				".inputs a b", // 2
				".outputs c", // 3
				".names a b c", // 4
				"11 1", // 5
				".end"); // 6
		assertNextLine(1, ".model", "simple");
		assertNextLine(2, ".inputs", "a", "b");
		assertNextLine(3, ".outputs", "c");
		assertNextLine(4, ".names", "a", "b", "c");
		assertNextLine(5, "11", "1");
		assertNextLine(6, ".end");
		assertNull(reader.nextLine());
		diag.assertNoMessages();
	}

	@Test
	public void testContinuedLines() throws IOException, AbortedException {
		createReader(".model\\", // 1
				" simple", // 2
				"  \t  ", // 3
				" .names  \\", // 4
				"a\\", // 5
				" \tb     c\\", // 6
				"     \\", // 7
				"", // 8
				"11 1", // 9
				"\\", // 10
				"   .end    ", // 11
				"          \\", // 12
				"      ");
		// lines are combined and then slit into whitespace-delimited fields.
		// the line number reported is the line on which the logical line starts
		assertNextLine(1, ".model", "simple");
		assertNextLine(4, ".names", "a", "b", "c");
		assertNextLine(9, "11", "1");
		assertNextLine(10, ".end");
		// trailing empty line handled correctly
		assertNull(reader.nextLine());
		diag.assertNoMessages();
	}

	@Test
	public void testComments() throws IOException, AbortedException {
		createReader("# simple model", // 1
				".model\\", // 2
				" simple\\", // 3
				"# ??", // 4
				" .names  a b c # d e f", // 5
				"0- 1", // 6
				"  # not quite empty", // 7
				".end"); // 8
		// all-comment lines are completely ignored
		assertNextLine(2, ".model", "simple");
		// trailing comments are stripped
		assertNextLine(5, ".names", "a", "b", "c");
		assertNextLine(6, "0-", "1");
		assertNextLine(8, ".end");
		assertNull(reader.nextLine());
		diag.assertNoMessages();
	}

	@Test
	public void testNoContinueCommentLine() throws IOException, AbortedException {
		createReader(".model simple    # simple model \\", // 1
				".names\\", // 2
				" foo bar baz\\# not extended either", // 3
				".end"); // 4
		// comment lines cannot be extended with a backslash. because this is probably
		// not what we wanted, that's a warning
		assertNextLine(1, ".model", "simple");
		diag.assertNumWarnings(1);
		diag.assertNumInfos(0);
		diag.assertNumErrors(0);
		// lines cannot be extended by a backslash directly before comment either
		assertNextLine(2, ".names", "foo", "bar", "baz\\");
		diag.assertNumWarnings(2);
		diag.assertNumInfos(0);
		diag.assertNumErrors(0);
		// the must not have been swallowed by the preceeding comment line
		assertNextLine(4, ".end");
		assertNull(reader.nextLine());
		diag.assertNumWarnings(2);
		diag.assertNumInfos(0);
		diag.assertNumErrors(0);
	}

	@Test
	public void testContinueIntoEOF() throws IOException, AbortedException {
		createReader("# simple model", // 1
				".model\\", // 2
				" simple\\"); // 3
		assertThrows(AbortedException.class, reader::nextLine);
		diag.assertNumErrors(1);
		diag.assertNumWarnings(0);
		diag.assertNumInfos(0);
	}

	private void createReader(final String... strings) {
		diag = new DiagnosticsShim();
		reader = new BlifReader(diag,
				new ByteArrayInputStream(String.join("\n", strings).getBytes(StandardCharsets.UTF_8)), "test.blif");
	}

	private void assertNextLine(final int lineno, final String... fields) throws IOException, AbortedException {
		final List<String> line = reader.nextLine();
		final SourceLocation sloc = reader.getCurrentLocation();
		assertEquals(List.of(fields), line);
		assertEquals("test.blif", sloc.getFilename());
		assertEquals(lineno, sloc.getLineNumber());
	}
}
