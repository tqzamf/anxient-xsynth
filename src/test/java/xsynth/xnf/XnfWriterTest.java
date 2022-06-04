package xsynth.xnf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import xsynth.naming.Name;
import xsynth.naming.Namespace;
import xsynth.naming.SpecialName;

public class XnfWriterTest {
	@Test
	public void testToggle() throws IOException {
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (final XnfWriter xnf = new XnfWriter(buffer)) {
			final Namespace ns = new Namespace(false);
			final Name feedback = ns.getGlobal("feedback");
			final Name enable = ns.getGlobal("enable");
			final Name enablePad = ns.getAnonymous(enable, "PAD");
			final Name output = ns.getGlobal("output");
			final Name outputPad = ns.getAnonymous(output, "PAD");
			final Name outputBuf = ns.getAnonymous(output, "BUF");
			final Name clock = ns.getGlobal("clock");
			final Name clockOsc = ns.getAnonymous(clock, "OSC");
			final Name logic1 = ns.getSpecial(SpecialName.VCC);
			ns.resolve();

			xnf.writeHeader(ns, "3030pc84-70", List.of("--testcase"));

			final XnfGate and = new XnfGate("AND", feedback, null);
			and.connect(PinDirection.CONSUMER, "I0", true, output, null);
			and.connect(PinDirection.CONSUMER, "I1", false, enable, null);
			and.connect(PinDirection.CONSUMER, "I2", false, logic1, null);
			and.connect(PinDirection.DRIVER, "O", false, feedback, null);
			xnf.writeSymbol(and);

			final XnfGate ff = new XnfGate("DFF", output, null);
			ff.connect(PinDirection.CONSUMER, "D", false, feedback, null);
			ff.connect(PinDirection.CONSUMER, "C", false, clock, null);
			ff.connect(PinDirection.DRIVER, "Q", false, output, null);
			xnf.writeSymbol(ff);

			final XnfPad enpad = new XnfPad(PadType.OUTPUT, enablePad, "P2", null, null);
			xnf.writePad(enpad);
			final XnfGate enbuf = new XnfGate("IBUF", enable, null);
			enbuf.connect(PinDirection.CONSUMER, "I", false, enablePad, null);
			enbuf.connect(PinDirection.DRIVER, "O", false, enable, null);
			xnf.writeSymbol(enbuf);

			final XnfPad outpad = new XnfPad(PadType.OUTPUT, outputPad, "P3", null, List.of("FAST"));
			xnf.writePad(outpad);
			final XnfGate outbuf = new XnfGate("OBUF", outputBuf, null);
			outbuf.connect(PinDirection.CONSUMER, "I", false, output, null);
			outbuf.connect(PinDirection.DRIVER, "O", false, outputPad, null);
			xnf.writeSymbol(outbuf);

			final XnfGate osc = new XnfGate("OSC", clockOsc, null);
			osc.connect(PinDirection.DRIVER, "O", false, clockOsc, null);
			xnf.writeSymbol(osc);
			final XnfGate abuf = new XnfGate("ACLK", clock, null);
			abuf.connect(PinDirection.CONSUMER, "I", false, clockOsc, null);
			abuf.connect(PinDirection.DRIVER, "O", false, clock, null);
			xnf.writeSymbol(abuf);
		}
		// try (final FileOutputStream xnf = new FileOutputStream("test.xnf")) {
		// xnf.write(buffer.toByteArray());
		// }
		assertIdenticalXnf(getClass(), "toggle.xnf", buffer);
	}

	public static void assertIdenticalXnf(final Class<?> reference, final String resource,
			final ByteArrayOutputStream buffer) {
		final List<String> lines = new ArrayList<>();
		try (final BufferedReader in = new BufferedReader(
				new InputStreamReader(reference.getResourceAsStream(resource)))) {
			while (true) {
				final String line = in.readLine();
				if (line == null)
					break;
				lines.add(line);
			}
		} catch (final IOException e) {
			throw new RuntimeException("IOException reading from resource!");
		}

		final String[] xnf = new String(buffer.toByteArray(), StandardCharsets.US_ASCII).split("\r\n");
		int i = 0;
		for (final String expected : lines) {
			assertTrue(i < xnf.length, "EOF in file, expecting " + expected);
			final String actual = xnf[i].replaceAll("\\s+,\\s+", ",");
			if (expected.startsWith("PROG,")) // contains version + date, which are dynamic
				assertTrue(actual.startsWith("PROG,"), "line " + i);
			else
				assertEquals(expected, actual, "line " + i);
			i++;
		}
		if (xnf.length > i)
			fail("expecting EOF, found line " + xnf[i]);
	}
}
