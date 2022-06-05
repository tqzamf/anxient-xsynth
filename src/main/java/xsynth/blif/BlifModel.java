package xsynth.blif;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.SourceLocation;

public class BlifModel {
	private final Map<String, String> buffers = new LinkedHashMap<>();
	private final Map<String, List<BlifGate>> consumers = new LinkedHashMap<>();
	private final Map<String, BlifGate> driver = new LinkedHashMap<>();
	private final List<BlifGate> gates = new ArrayList<>();
	private final Set<String> clocks = new HashSet<>();
	private Set<String> inputs;
	private Set<String> outputs;
	private final String name;
	private final SourceLocation sloc;

	BlifModel(final String name, final SourceLocation sloc) {
		this.name = name;
		this.sloc = sloc;
	}

	void inferIO(final Diagnostics diag) throws AbortedException {
		// if unspecified, inputs are the toplevel nets that aren't driven by anything.
		// because every net does need to have a driver, all inferred inputs must have
		// been declared as inputs, else they are undriven. if there are inferred inputs
		// that aren't declared, warn and set them to zero (to simplify downstream
		// logic). if declared inputs aren't used, that's silly but harmless, and for
		// subcircuits, it's actually the correct way of declaring a signal as unused.
		// we also treat all clocks as inputs for simplicity. they are a special kind of
		// input, and it's easier to occasionally check whether an input is a clock,
		// than constantly having to handle two lists.
		final Set<String> inferredInputs = new HashSet<>(consumers.keySet());
		inferredInputs.removeAll(driver.keySet());
		if (inputs != null) {
			inputs.addAll(clocks);
			inferredInputs.removeAll(inputs);
			for (final String in : inferredInputs) {
				diag.warn(sloc, "undriven signal, assuming zero: " + in);
				addGate(new SumOfProducts(in, List.of())); // no terms, thus zero output
			}
		} else {
			inferredInputs.addAll(clocks);
			inputs = inferredInputs;
		}

		// likewise, outputs are the toplevel nets that aren't used by anything. of
		// course, toplevel signals that *are* used could well be useful as outputs, but
		// that's really just what the output declaration is for.
		// we do list generated signals that aren't actually output by the module,
		// simply because they may not be what the designer intended.
		final Set<String> inferredOutputs = new HashSet<>(driver.keySet());
		inferredOutputs.removeAll(consumers.keySet());
		if (outputs != null) {
			inferredOutputs.removeAll(outputs);
			if (!inferredOutputs.isEmpty())
				diag.info(sloc, "unused signals: " + String.join(" ", inferredOutputs));
		} else
			outputs = inferredOutputs;

		// if a .clock is specified, then it will be treated as a clock input and thus
		// cannot be *generated* by the circuit itself
		AbortedException err = null;
		for (final String clk : clocks)
			if (driver.containsKey(clk))
				err = diag.error(sloc, "clock " + clk + " is driven in circuit");
		if (err != null)
			throw err;
	}

	public void addGate(final BlifGate gate) {
		gates.add(gate);
		for (final String out : gate.getOutputs()) {
			if (driver.containsKey(out))
				throw new IllegalArgumentException(
						"output " + out + " driven by multiple gates: " + driver.get(out) + " and " + gate);
			driver.put(out, gate);
		}
		for (final String in : gate.getInputs()) {
			if (!consumers.containsKey(in))
				consumers.put(in, new ArrayList<>());
			consumers.get(in).add(gate);
		}
	}

	public List<BlifGate> getGates() {
		return gates;
	}

	public void addInputs(final List<String> names) {
		if (inputs == null)
			inputs = new HashSet<>();
		inputs.addAll(names);
	}

	public Set<String> getInputs() {
		return inputs;
	}

	public void addOutputs(final List<String> names) {
		if (outputs == null)
			outputs = new HashSet<>();
		outputs.addAll(names);
	}

	public Set<String> getOutputs() {
		return outputs;
	}

	public void addClocks(final List<String> names) {
		clocks.addAll(names);
	}

	public Set<String> getClocks() {
		return clocks;
	}

	public void addBuffers(final String bufferType, final List<String> names) throws IllegalArgumentException {
		for (final String name : names) {
			if (buffers.containsKey(name))
				throw new IllegalArgumentException(
						"conflicting buffer types for " + name + ": " + buffers.get(name) + " and " + bufferType);
			buffers.put(name, bufferType);
		}
	}

	public Map<String, String> getBuffers() {
		return buffers;
	}

	public String getBuffer(final String name) {
		return buffers.get(name);
	}

	public String getName() {
		return name;
	}

	public SourceLocation getSourceLocation() {
		return sloc;
	}
}
