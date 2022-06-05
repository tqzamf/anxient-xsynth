package xsynth.xnf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import xsynth.naming.Name;
import xsynth.naming.Namespace;
import xsynth.xnf.XnfNetlist.Term;

public class XnfNetlistTest {
	@ParameterizedTest
	@ValueSource(ints = { 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 })
	public void testMinMaxGateInputs(final int numInputs) throws IOException {
		final Namespace ns = new Namespace(false);
		final Name output = ns.getGlobal("output");
		final List<Term> inputs = new ArrayList<>();
		for (int i = 0; i < numInputs; i++)
			inputs.add(new Term(ns.getGlobal("in" + i), false));
		final XnfNetlist netlist = new XnfNetlist(3, false, false);
		netlist.addLogicGate("AND", output, false, inputs);

		for (final XnfGate gate : netlist.getGates()) {
			final List<XnfPin> inpins = new ArrayList<>();
			XnfPin outpin = null;
			for (final XnfPin pin : gate.getPins())
				if (pin.getDir() == PinDirection.DRIVER) {
					assertNull(outpin, "multiple output pins in gate for " + outpin);
					outpin = pin;
				} else
					inpins.add(pin);
			assertNotNull(outpin, "no output pin!");
			assertTrue(inpins.size() <= 3, "too many input pins: " + inputs);
			assertTrue(inpins.size() >= 2, "too few input pins: " + inputs);
		}
	}

	@Test
	public void testMaxGateDepth() throws IOException {
		final Namespace ns = new Namespace(false);
		final Name output = ns.getGlobal("output");
		final List<Term> inputs = new ArrayList<>();
		for (int i = 0; i < 257; i++)
			inputs.add(new Term(ns.getGlobal("in" + i), false));
		final XnfNetlist netlist = new XnfNetlist(3, false, false);
		netlist.addLogicGate("AND", output, false, inputs);

		final Map<Name, Integer> depth = inputs.stream().collect(Collectors.toMap(Term::name, x -> 0));
		for (int i = 0; i < 259; i++)
			for (final XnfGate gate : netlist.getGates()) {
				int max = Integer.MIN_VALUE;
				boolean defined = true;
				Name outsig = null;
				for (final XnfPin pin : gate.getPins())
					if (pin.getDir() == PinDirection.CONSUMER) {
						final Integer d = depth.get(pin.getSignal());
						if (d == null)
							defined = false;
						else if (d > max)
							max = d;
					} else
						outsig = pin.getSignal();
				if (defined)
					depth.put(outsig, max + 1);
			}

		// 85 for first layer, 2 left over
		// 29 for second layer, none left over
		// 9 for third layer, 2 left over
		// 3 for fourth layer, 2 left over
		// 2 for fifth layer, 2 left ofer
		// and a single sixth layer
		// → doable in a total of 129 gates and 6 levels
		assertEquals(6, depth.get(output));
		assertTrue(netlist.getGates().size() < 129, "algorithm wastes gates: " + netlist.getGates().size());
	}

	@Test
	public void testMinGateSizeSafetyCheck() throws IOException {
		final Namespace ns = new Namespace(false);
		final Name output = ns.getGlobal("output");
		final Name input = ns.getGlobal("input");
		final XnfNetlist netlist = new XnfNetlist(3, false, false);
		assertThrows(IllegalArgumentException.class,
				() -> netlist.addLogicGate("AND", output, false, List.of(new Term(input, false))));
	}

	@Test
	public void testLatches() throws IOException {
		final XnfNetlist netlist = new XnfNetlist(4, true, true);
		final Namespace ns = createTestLatches(netlist);

		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (final XnfWriter xnf = new XnfWriter(buffer)) {
			xnf.writeHeader(ns, "3030pc84-70", List.of("--testcase"));
			xnf.writeNetlist(netlist);
		}
		// try (final FileOutputStream xnf = new FileOutputStream("test.xnf")) {
		// xnf.write(buffer.toByteArray());
		// }
		XnfWriterTest.assertIdenticalXnf(getClass(), "latches.xnf", buffer);
	}

	@Test
	public void testEmulatedLatches() throws IOException {
		final XnfNetlist netlist = new XnfNetlist(4, true, false);
		final Namespace ns = createTestLatches(netlist);

		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (final XnfWriter xnf = new XnfWriter(buffer)) {
			xnf.writeHeader(ns, "3030pc84-70", List.of("--testcase"));
			xnf.writeNetlist(netlist);
		}
//		try (final FileOutputStream xnf = new FileOutputStream("test.xnf")) {
//			xnf.write(buffer.toByteArray());
//		}
		XnfWriterTest.assertIdenticalXnf(getClass(), "emulatches.xnf", buffer);
	}

	private Namespace createTestLatches(final XnfNetlist netlist) throws IOException {
		final Namespace ns = new Namespace(false);
		final Name data1 = ns.getGlobal("data1");
		final Name data2 = ns.getGlobal("data2");
		final Name clock = ns.getGlobal("clock");
		final Name re = ns.getGlobal("re");
		final Name fe = ns.getGlobal("fe");
		final Name ah = ns.getGlobal("ah");
		final Name al = ns.getGlobal("al");

		netlist.addLatch(LatchType.FLIPFLOP, false, re, data1, clock, false);
		netlist.addLatch(LatchType.FLIPFLOP, true, fe, data2, clock, true);
		netlist.addLatch(LatchType.LATCH, true, ah, data1, clock, false);
		netlist.addLatch(LatchType.LATCH, false, al, data2, clock, true);
		ns.resolve();
		return ns;
	}

	@Test
	public void testNoLatchesSafetyCheck() throws IOException {
		final Namespace ns = new Namespace(false);
		final Name output = ns.getGlobal("output");
		final Name input = ns.getGlobal("input");
		final XnfNetlist netlist = new XnfNetlist(3, false, false);
		assertThrows(IllegalArgumentException.class,
				() -> netlist.addLatch(LatchType.LATCH, false, output, input, input, false));
	}

}
