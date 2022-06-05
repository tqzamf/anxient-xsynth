package xsynth.blif;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.DiagnosticsShim;
import xsynth.SourceLocation;

public class CustomGateTest {
	private DiagnosticsShim diag;
	private BlifModel model;
	private CustomGateShim gate;

	@Test
	public void testCustomGate() throws IOException, AbortedException {
		parseGate(List.of("i"), List.of("o"), List.of(), List.of(), ".gate testgate i=in o=out");
		assertIO(Map.of("i", "in"), Map.of("o", "out"));
		assertEquals(List.of(), gate.getFlags());
		diag.assertNoMessages();

		// if there are no required pins, then it's fine to have no connections.
		// this can actually be useful to instantiate eg. RDBK with default values.
		parseGate(List.of("i"), List.of("o"), List.of(), List.of(), ".gate testgate");
		assertIO(Map.of(), Map.of());
		assertEquals(List.of(), gate.getFlags());
		diag.assertNoMessages();

		/// the complex example:
		// - pin names and flags are case-insensitive and automatically lowercased, as
		//   is the gate name.
		// - net names, however, are case sensitive and case-preserving. they can also
		//   contain an equals sign, making it a stupid but legal BLIF name.
		// - flags can be everywhere (though for readability, they probably shouldn't.)
		// - optional flags and pins can simply be omitted
		parseGate(List.of("a", "b", "c", "d"), List.of("y", "z", "w"), List.of("a", "b", "c", "y", "z"),
				List.of("and", "or", "xor"), ".gate TESTgate and a=a B=BAr OR c=cat y=Z z==z=");
		assertIO(Map.of("a", "a", "b", "BAr", "c", "cat"), Map.of("y", "Z", "z", "=z="));
		assertEquals(List.of("and", "or"), gate.getFlags());
		diag.assertNoMessages();
	}

	@Test
	public void testIoPad() throws IOException, AbortedException {
		// IO pads have this weird thing where i is an output and o is an input, because
		// they're named relative to the chip and an output off the chip obviously needs
		// to be driven from within the chip
		// pads use the "iopad" gate, and actually uppercase their names because pin
		// names are invariably specified in uppercase.
		parsePad(".pad p15 i=in o=out t=tris fast");
		assertEquals("P15", gate.getName());
		assertIO(Map.of("o", "out", "t", "tris"), Map.of("i", "in"));
		assertEquals(List.of("fast"), gate.getFlags());
		diag.assertNoMessages();

		// the useful combinations are: input, output, tri-state output, and tri-state
		// output with input
		parsePad(".pad P12 i=clk");
		assertIO(Map.of(), Map.of("i", "clk"));
		assertEquals(List.of(), gate.getFlags());
		diag.assertNoMessages();
		parsePad(".pad P12 o=clk");
		assertIO(Map.of("o", "clk"), Map.of());
		assertEquals(List.of(), gate.getFlags());
		diag.assertNoMessages();
		parsePad(".pad P12 o=bar t=tri");
		assertIO(Map.of("t", "tri", "o", "bar"), Map.of());
		assertEquals(List.of(), gate.getFlags());
		diag.assertNoMessages();
		parsePad(".pad P12 i=foo o=bar t=tri");
		assertIO(Map.of("t", "tri", "o", "bar"), Map.of("i", "foo"));
		assertEquals(List.of(), gate.getFlags());
		diag.assertNoMessages();
		// output with pin feedback isn't terribly useful. except maybe to detect when a
		// pin is being overdriven, to turn off the driver in that case
		parsePad(".pad P12 i=foo o=bar slow");
		assertIO(Map.of("o", "bar"), Map.of("i", "foo"));
		assertEquals(List.of("slow"), gate.getFlags());
		diag.assertNoMessages();
		// the semantics aren't handled in the parser, so cannot be tested here. for
		// example, not specifying any connections is illegal, as is connecting the
		// tristate input but providing no output value.
		// also, error cases are tested by testIllegalGate() because pads share that
		// piece of code with custom gates.
	}

	private void assertIO(final Map<String, String> inputs, final Map<String, String> outputs) {
		assertEquals(inputs, gate.getInputMap());
		assertEquals(outputs, gate.getOutputMap());
		assertEquals(new HashSet<>(inputs.values()), new HashSet<>(gate.getInputs()));
		assertEquals(new HashSet<>(outputs.values()), new HashSet<>(gate.getOutputs()));
	}

	@Test
	public void testIllegalGate() throws IOException, AbortedException {
		// gate name missing or gate isn't declared
		assertParseDiagnostics(1, 0, 0, List.of("i"), List.of("o"), List.of(), List.of(), ".gate");
		assertParseDiagnostics(1, 0, 0, List.of("i"), List.of("o"), List.of(), List.of(), ".gate gatetest");
		// invalid pin and flag names
		assertParseDiagnostics(1, 0, 0, List.of("i"), List.of("o"), List.of(), List.of(), ".gate testgate i=o o=i a=b");
		assertParseDiagnostics(1, 0, 0, List.of("i"), List.of("o"), List.of(), List.of(), ".gate testgate c=d");
		assertParseDiagnostics(1, 0, 0, List.of("i"), List.of("o"), List.of(), List.of(), ".gate testgate nor");
		assertParseDiagnostics(1, 0, 0, List.of(), List.of(), List.of(), List.of("nand"), ".gate testgate nor");
		// connecting the same pin twice
		assertParseDiagnostics(1, 0, 0, List.of("i"), List.of("o"), List.of(), List.of(), ".gate testgate i=o i=i");
		assertParseDiagnostics(1, 0, 0, List.of("i"), List.of("o"), List.of(), List.of(), ".gate testgate o=i o=o");
		// not connecting a requried pin
		assertParseDiagnostics(1, 0, 0, List.of("i"), List.of("o"), List.of("i"), List.of(), ".gate testgate o=i");
		assertParseDiagnostics(1, 0, 0, List.of("i"), List.of("o"), List.of("o"), List.of(), ".gate testgate i=o");
		assertParseDiagnostics(1, 0, 0, List.of("i", "q"), List.of("o"), List.of("q"), List.of(), ".gate testgate i=o");
		// empty pin or signal name
		assertParseDiagnostics(1, 0, 0, List.of(""), List.of(), List.of(), List.of(), ".gate testgate =o");
		assertParseDiagnostics(1, 0, 0, List.of("i"), List.of(), List.of(), List.of(), ".gate testgate i=");
		assertParseDiagnostics(2, 0, 0, List.of(""), List.of(), List.of(), List.of(), ".gate testgate =");
		// there is no "junk at the end" case because that junk is just considered a
		// funnily-named (and probably undefined) flag
	}

	@Test
	public void testDuplicateDriver() throws IOException, AbortedException {
		// it's an error to connect multiple outputs to the same net
		assertParseDiagnostics(1, 0, 0, List.of("i"), List.of("o", "q"), List.of(), List.of(),
				".gate testgate i=a o=b q=b");
		// but two inputs can be tied together
		assertParseDiagnostics(0, 0, 0, List.of("a", "b"), List.of("o"), List.of(), List.of(),
				".gate testgate a=i b=i o=q");
	}

	private void assertParseDiagnostics(final int errors, final int warnings, final int infos,
			final List<String> inputs, final List<String> outputs, final List<String> required,
			final List<String> flags, final String line) {
		if (errors > 0)
			assertThrows(AbortedException.class, () -> parseGate(inputs, outputs, required, flags, line));
		else
			assertDoesNotThrow(() -> parseGate(inputs, outputs, required, flags, line));
		diag.assertNumMessages(errors, warnings, infos);
	}

	private void parseGate(final List<String> inputs, final List<String> outputs, final List<String> required,
			final List<String> flags, final String decl) throws IOException, AbortedException {
		parse("testgate", inputs, outputs, required, flags, decl);
		assertEquals("testgate", gate.getName());
	}

	private void parsePad(final String decl) throws IOException, AbortedException {
		parse(CustomGateFactory.IOPAD_GATE, List.of("o", "t"), List.of("i"), List.of(), List.of("fast", "slow"), decl);
	}

	private void parse(final String gatename, final List<String> inputs, final List<String> outputs,
			final List<String> required, final List<String> flags, final String decl)
			throws IOException, AbortedException {
		diag = new DiagnosticsShim();
		try (ByteArrayInputStream data = new ByteArrayInputStream(
				(".model test\n" + decl).getBytes(StandardCharsets.UTF_8))) {
			final BlifParser parser = new BlifParser(diag,
					Map.of(gatename, new CustomGateFactoryShim(inputs, outputs, required, flags)));
			model = parser.parse(data, "test.blif");
		}
		assertNotNull(model);
		assertEquals(1, model.getGates().size());
		final BlifGate gate = model.getGates().get(0);
		assertEquals(CustomGateShim.class, gate.getClass());
		this.gate = (CustomGateShim) gate;
	}

	private static class CustomGateFactoryShim implements CustomGateFactory {
		private final List<String> inputs;
		private final List<String> outputs;
		private final List<String> required;
		private final List<String> flags;

		public CustomGateFactoryShim(final List<String> inputs, final List<String> outputs, final List<String> required,
				final List<String> flags) {
			this.inputs = inputs;
			this.outputs = outputs;
			this.required = required;
			this.flags = flags;
		}

		@Override
		public CustomGate newInstance(final Diagnostics diag, final SourceLocation sloc, final String name,
				final List<String> flags, final Map<String, String> outputs, final Map<String, String> inputs)
				throws AbortedException {
			return new CustomGateShim(name, flags, inputs, outputs);
		}

		@Override
		public List<String> getInputs() {
			return inputs;
		}

		@Override
		public List<String> getOutputs() {
			return outputs;
		}

		@Override
		public List<String> getFlags() {
			return flags;
		}

		@Override
		public List<String> getRequiredSignals() {
			return required;
		}

	}

	private static class CustomGateShim extends CustomGate {
		private final String name;
		private final List<String> flags;
		private final Map<String, String> inputs;
		private final Map<String, String> outputs;

		public CustomGateShim(final String name, final List<String> flags, final Map<String, String> inputs,
				final Map<String, String> outputs) {
			super(outputs, inputs);
			this.name = name;
			this.flags = flags;
			this.inputs = inputs;
			this.outputs = outputs;
		}

		public Map<String, String> getInputMap() {
			return inputs;
		}

		public Map<String, String> getOutputMap() {
			return outputs;
		}

		public String getName() {
			return name;
		}

		public List<String> getFlags() {
			return flags;
		}
	}
}
