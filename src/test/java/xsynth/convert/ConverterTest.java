package xsynth.convert;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
		// two warnings regarding undriven global inputs
		// one info listing all the unused global outputs
		diag.assertNumMessages(0, 2, 1);
	}

	@Test
	public void testSumOfProductsConstants() throws IOException, AbortedException {
		final DiagnosticsShim diag = convert("xc3030avg100-7", "constants");
		// one info listing the unused global outputs
		diag.assertNumMessages(0, 0, 1);
	}

	@Test
	public void testSumOfProductsInversion() throws IOException, AbortedException {
		final DiagnosticsShim diag = convert("xc3030avg100-7", "invert");
		// two warnings regarding undriven global inputs
		// one info listing all the unused global outputs
		diag.assertNumMessages(0, 2, 1);
	}

	@Test
	public void testLatches() throws IOException, AbortedException {
		final DiagnosticsShim diag = convert("xc2064pd48-50", "latches");
		// two warnings regarding undriven global inputs
		// one info listing all the unused global outputs
		diag.assertNumMessages(0, 2, 1);
	}

	@Test
	public void testLatchGlobalClock() throws IOException, AbortedException {
		final DiagnosticsShim diag = convert("xc3030avg100-7", "gclk");
		// two warnings regarding undriven global inputs
		// one info listing all the unused global outputs
		diag.assertNumMessages(0, 2, 1);
	}

	@ParameterizedTest
	@MethodSource("getPadTestPairs")
	public void testPads(final String part, final String filename) throws IOException, AbortedException {
		final DiagnosticsShim diag = convert(part, filename);
		// two of the pads have a connection to t but not o, which generates a warning
		// message
		// plus 6 warnings regarding undriven global inputs
		// plus one info listing all the unused global outputs
		diag.assertNumMessages(0, 8, 1);
	}

	@Test
	public void testMerge() throws IOException, AbortedException {
		final DiagnosticsShim diag = new DiagnosticsShim();
		final Converter converter = new Converter(diag, ChipFamily.forPart("2064pd48-50"), false);
		converter.read(getClass().getResourceAsStream("blinker.blif"), "blinker.blif");
		converter.read(getClass().getResourceAsStream("blinkerio.blif"), "blinkerio.blif");
		try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
			converter.writeTo(buffer, "2064pd48-50", List.of("--testcase", "merge"));
			XnfWriterTest.assertIdenticalXnf(getClass(), "blinker.xnf", buffer);
		}
		// 1 warning about the undrinve global reset signal
		// 2 infos about _LOGIC0 (create by iverilog's tgt-blif) being unused
		// 1 info about blinkerio being implicitly named
		diag.assertNumMessages(0, 1, 3);
	}

	@Test
	public void testMergeMultipleDrivers() throws IOException, AbortedException {
		final DiagnosticsShim diag = new DiagnosticsShim();
		final Converter converter = new Converter(diag, ChipFamily.forPart("2064pd48-50"), false);
		converter.read(getClass().getResourceAsStream("driver1.blif"), "driver1.blif");
		try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
			assertThrows(AbortedException.class,
					() -> converter.read(getClass().getResourceAsStream("driver2.blif"), "driver2.blif"));
		}
		// 2 errors because test1 and test2 are multiply driven
		// 2 infos about the other place where they're driven
		diag.assertNumMessages(2, 0, 2);
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
