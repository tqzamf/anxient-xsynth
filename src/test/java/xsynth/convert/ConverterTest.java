package xsynth.convert;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import xsynth.Diagnostics.AbortedException;
import xsynth.DiagnosticsShim;
import xsynth.chips.ChipFamily;
import xsynth.xnf.XnfWriterTest;

public class ConverterTest {
	@Test
	public void testSumOfProducts() throws IOException, AbortedException {
		final DiagnosticsShim diag = convert("xc3030avg100-7", "sop");
		diag.assertNumMessages(0, 0, 0);
	}

	@Test
	public void testSumOfProductsConstants() throws IOException, AbortedException {
		final DiagnosticsShim diag = convert("xc3030avg100-7", "constants");
		diag.assertNumMessages(0, 0, 0);
	}

	@Test
	public void testSumOfProductsInversion() throws IOException, AbortedException {
		final DiagnosticsShim diag = convert("xc3030avg100-7", "invert");
		diag.assertNumMessages(0, 0, 0);
	}

	@Test
	public void testLatches() throws IOException, AbortedException {
		final DiagnosticsShim diag = convert("xc2064pd48-50", "latches");
		diag.assertNumMessages(0, 0, 0);
	}

	@Test
	public void testLatchGlobalClock() throws IOException, AbortedException {
		final DiagnosticsShim diag = convert("xc3030avg100-7", "gclk");
		diag.assertNumMessages(0, 0, 0);
	}

	@ParameterizedTest
	@MethodSource("getPadTestPairs")
	public void testPads(final String part, final String filename) throws IOException, AbortedException {
		final DiagnosticsShim diag = convert(part, filename);
		// two of the pads have a connection to t but not o, which generates a warning
		// message
		diag.assertNumMessages(0, 2, 0);
	}

	@Test
	public void testMerging() throws IOException, AbortedException {
		final DiagnosticsShim diag = new DiagnosticsShim();
		final Converter converter = new Converter(diag, ChipFamily.forPart("2064pd48-50"), false);
		converter.read(getClass().getResourceAsStream("blinker.blif"), "blinker.blif");
		converter.read(getClass().getResourceAsStream("blinkerio.blif"), "blinkerio.blif");
		try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
			converter.writeTo(buffer, "2064pd48-50", List.of("--testcase", "merge"));
			XnfWriterTest.assertIdenticalXnf(getClass(), "blinker.xnf", buffer);
		}
		diag.assertNumMessages(0, 0, 3);
	}

	private DiagnosticsShim convert(final String part, final String filename) throws IOException, AbortedException {
		final DiagnosticsShim diag = new DiagnosticsShim();
		final Converter converter = new Converter(diag, ChipFamily.forPart(part), false);
		converter.read(getClass().getResourceAsStream(filename + ".blif"), filename + ".blif");
		try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
			converter.writeTo(buffer, part, List.of("--testcase", filename));
			XnfWriterTest.assertIdenticalXnf(getClass(), filename + ".xnf", buffer);
		}
		return diag;
	}

	public static String[][] getPadTestPairs() {
		return new String[][] { //
				{ "xc2064pd48-50", "pads2k" }, //
				{ "xc3195apc84-2", "pads3k" }, //
				{ "xc5202pq100-5", "pads5k2" } };
	}
}
