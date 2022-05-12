package xsynth.blif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

public class BlifReaderTest {
	@Test
	public void testEmptyFile() throws IOException {
		final BlifReader reader = createReader("");
		// null at EOF. calling again gives null again
		assertNull(reader.nextLine());
		assertNull(reader.nextLine());
	}

	@Test
	public void testEndOfLine() throws IOException {
		// all 4 EOL common conditions: CR, LF, CRLF, and EOF
		final BlifReader reader = createReader("a\nb\rc\r\nd");
		assertEquals(List.of("a"), reader.nextLine());
		assertEquals(1, reader.getLineNumber());
		assertEquals(List.of("b"), reader.nextLine());
		assertEquals(2, reader.getLineNumber());
		assertEquals(List.of("c"), reader.nextLine());
		assertEquals(3, reader.getLineNumber());
		assertEquals(List.of("d"), reader.nextLine());
		assertEquals(4, reader.getLineNumber());
		assertNull(reader.nextLine());
	}

	@Test
	public void testSimpleModel() throws IOException {
		final BlifReader reader = createReader(".model simple", // 1
				".inputs a b", // 2
				".outputs c", // 3
				".names a b c", // 4
				"11 1", // 5
				".end"); // 6
		assertEquals(List.of(".model", "simple"), reader.nextLine());
		assertEquals(1, reader.getLineNumber());
		assertEquals(List.of(".inputs", "a", "b"), reader.nextLine());
		assertEquals(2, reader.getLineNumber());
		assertEquals(List.of(".outputs", "c"), reader.nextLine());
		assertEquals(3, reader.getLineNumber());
		assertEquals(List.of(".names", "a", "b", "c"), reader.nextLine());
		assertEquals(4, reader.getLineNumber());
		assertEquals(List.of("11", "1"), reader.nextLine());
		assertEquals(5, reader.getLineNumber());
		assertEquals(List.of(".end"), reader.nextLine());
		assertEquals(6, reader.getLineNumber());
		assertNull(reader.nextLine());
	}

	@Test
	public void testContinuedLines() throws IOException {
		final BlifReader reader = createReader(".model\\", // 1
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
		assertEquals(List.of(".model", "simple"), reader.nextLine());
		assertEquals(1, reader.getLineNumber());
		assertEquals(List.of(".names", "a", "b", "c"), reader.nextLine());
		assertEquals(4, reader.getLineNumber());
		assertEquals(List.of("11", "1"), reader.nextLine());
		assertEquals(9, reader.getLineNumber());
		assertEquals(List.of(".end"), reader.nextLine());
		assertEquals(10, reader.getLineNumber());
		// trailing empty line handled correctly
		assertNull(reader.nextLine());
	}

	@Test
	public void testComments() throws IOException {
		final BlifReader reader = createReader("# simple model", // 1
				".model\\", // 2
				" simple\\", // 3
				"# ??", // 4
				" .names  a b c # d e f \\", // 5
				"0- 1", // 6
				"  # not quite empty", // 7
				".names zero\\# not extended", // 8
				".end"); // 9
		// all-comment lines are completely ignored
		assertEquals(List.of(".model", "simple"), reader.nextLine());
		assertEquals(2, reader.getLineNumber());
		// trailing comments are stripped, and comment lines cannot be extended
		assertEquals(List.of(".names", "a", "b", "c"), reader.nextLine());
		assertEquals(5, reader.getLineNumber());
		assertEquals(List.of("0-", "1"), reader.nextLine());
		assertEquals(6, reader.getLineNumber());
		// lines cannot be extended by backslash directly before comment either
		assertEquals(List.of(".names", "zero\\"), reader.nextLine());
		assertEquals(8, reader.getLineNumber());
		assertEquals(List.of(".end"), reader.nextLine());
		assertEquals(9, reader.getLineNumber());
		assertNull(reader.nextLine());
	}

	private BlifReader createReader(final String... strings) {
		return new BlifReader(new ByteArrayInputStream(String.join("\n", strings).getBytes(StandardCharsets.UTF_8)));
	}
}
