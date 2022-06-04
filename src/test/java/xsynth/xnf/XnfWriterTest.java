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
import xsynth.xnf.XnfNetlist.Term;

public class XnfWriterTest {
	@Test
	public void testToggle() throws IOException {
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (final XnfWriter xnf = new XnfWriter(buffer)) {
			final Namespace ns = new Namespace(false);
			final Name feedback = ns.getGlobal("feedback");
			final Name enable = ns.getGlobal("enable");
			final Name enablePad = enable.getAnonymous("PAD");
			final Name output = ns.getGlobal("output");
			final Name outputPad = output.getAnonymous("PAD");
			final Name clock = ns.getGlobal("clock");
			final Name clockOsc = clock.getAnonymous("OSC");
			final Name logic1 = ns.getSpecial(SpecialName.VCC);
			ns.resolve();

			xnf.writeHeader(ns, "3030pc84-70", List.of("--testcase"));

			final XnfGate and = new XnfGate("AND", null);
			and.connect(PinDirection.CONSUMER, "I0", true, output, null);
			and.connect(PinDirection.CONSUMER, "I1", false, enable, null);
			and.connect(PinDirection.CONSUMER, "I2", false, logic1, null);
			and.connect(PinDirection.DRIVER, "O", false, feedback, null);
			xnf.writeSymbol(and);

			final XnfGate ff = new XnfGate("DFF", null);
			ff.connect(PinDirection.CONSUMER, "C", false, clock, null);
			ff.connect(PinDirection.CONSUMER, "D", false, feedback, null);
			ff.connect(PinDirection.DRIVER, "Q", false, output, null);
			xnf.writeSymbol(ff);

			final XnfGate enbuf = new XnfGate("IBUF", null);
			enbuf.connect(PinDirection.CONSUMER, "I", false, enablePad, null);
			enbuf.connect(PinDirection.DRIVER, "O", false, enable, null);
			xnf.writeSymbol(enbuf);

			final XnfGate outbuf = new XnfGate("OBUF", null);
			outbuf.connect(PinDirection.CONSUMER, "I", false, output, null);
			outbuf.connect(PinDirection.DRIVER, "O", false, outputPad, null);
			xnf.writeSymbol(outbuf);

			final XnfGate osc = new XnfGate("OSC", null);
			osc.connect(PinDirection.DRIVER, "O", false, clockOsc, null);
			xnf.writeSymbol(osc);
			final XnfGate abuf = new XnfGate("ACLK", null);
			abuf.connect(PinDirection.CONSUMER, "I", false, clockOsc, null);
			abuf.connect(PinDirection.DRIVER, "O", false, clock, null);
			xnf.writeSymbol(abuf);

			xnf.writePad(new XnfPad(PadType.INPUT, enablePad, "P2", null, null));
			xnf.writePad(new XnfPad(PadType.OUTPUT, outputPad, "P3", null, List.of("FAST")));
		}
		// try (final FileOutputStream xnf = new FileOutputStream("test.xnf")) {
		// xnf.write(buffer.toByteArray());
		// }
		assertIdenticalXnf(getClass(), "toggle.xnf", buffer);
	}

	@Test
	public void testToggleNetlist() throws IOException {
		final Namespace ns = new Namespace(false);
		final Name feedback = ns.getGlobal("feedback");
		final Name enable = ns.getGlobal("enable");
		final Name enablePad = enable.getAnonymous("PAD");
		final Name output = ns.getGlobal("output");
		final Name outputPad = output.getAnonymous("PAD");
		final Name clock = ns.getGlobal("clock");
		final Name clockOsc = clock.getAnonymous("OSC");
		final Name logic1 = ns.getSpecial(SpecialName.VCC);
		ns.resolve();

		final XnfNetlist netlist = new XnfNetlist(4, false, false);
		netlist.addLogicGate("AND", feedback,
				List.of(new Term(output, true), new Term(enable, false), new Term(logic1, false)));
		netlist.addLatch(LatchType.FLIPFLOP, false, output, feedback, clock, false);

		netlist.addBuffer("IBUF", enable, enablePad);
		netlist.addPad(PadType.INPUT, enablePad, "P2", null, null);
		netlist.addBuffer("OBUF", outputPad, output);
		netlist.addPad(PadType.OUTPUT, outputPad, "P3", null, List.of("FAST"));

		final XnfGate osc = netlist.addSymbol("OSC", null);
		osc.connect(PinDirection.DRIVER, "O", false, clockOsc, null);
		netlist.addBuffer("ACLK", clock, clockOsc);

		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (final XnfWriter xnf = new XnfWriter(buffer)) {
			xnf.writeHeader(ns, "3030pc84-70", List.of("--testcase"));
			xnf.writeNetlist(netlist);
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
			final String actual = xnf[i].replaceAll("\\s+,\\s+", ",").replaceFirst(",+$", "");
			i++;
			if (expected.startsWith("PROG,")) // contains version + date, which are dynamic
				assertTrue(actual.startsWith("PROG,"), "line " + i);
			else
				assertEquals(expected, actual, "line " + i);
		}
		if (xnf.length > i)
			fail("expecting EOF, found line " + xnf[i]);
	}
}
