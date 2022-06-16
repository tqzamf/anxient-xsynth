package xsynth.chips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import xsynth.blif.CustomGateFactory;

public class RAMFactoryTest {
	private final Map<String, CustomGateFactory> GATES = new XC4000Family().getCustomGates();

	@Test
	public void testInputOutputs() {
		assertInputsOutputs("RAM", List.of("A0", "A1", "A2", "A3", "A4"), false, "O");
		assertInputsOutputs("RAMS", List.of("A0", "A1", "A2", "A3", "A4"), true, "O");
		assertInputsOutputs("RAMD", List.of("A0", "A1", "A2", "A3", "DPRA0", "DPRA1", "DPRA2", "DPRA3"), true, "SPO",
				"DPO");
	}

	private void assertInputsOutputs(final String gateName, final List<String> addresses, final boolean hasWCLK,
			final String... outputPrefixes) {
		final CustomGateFactory gate = GATES.get(gateName);
		final Set<String> inputs = new HashSet<>(gate.getInputs());
		final int lastInput = removeAll(inputs, "D");
		assertTrue(lastInput > 10, "too few numbered inputs: " + lastInput);
		final Set<String> expectedInputs = new HashSet<>(addresses);
		expectedInputs.add("WE");
		expectedInputs.add("D");
		if (hasWCLK)
			expectedInputs.add("WCLK");
		assertEquals(expectedInputs, inputs);

		final Set<String> outputs = new HashSet<>(gate.getOutputs());
		for (final String prefix : outputPrefixes) {
			final int lastOutput = removeAll(outputs, prefix);
			assertTrue(lastOutput > 10, "too few numbered outputs: " + lastOutput);
			assertEquals(lastOutput, lastInput, "number of inputs and outputs differs");
		}
		assertEquals(Set.of(outputPrefixes), outputs);
	}

	private int removeAll(final Set<String> inputs, final String prefix) {
		int i = 0;
		while (inputs.contains(prefix + i)) {
			inputs.remove(prefix + i);
			i++;
		}
		return i;
	}
}
