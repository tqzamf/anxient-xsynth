package xsynth.blif;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import xsynth.SourceLocation;

public class BlifModel {
	private final Set<String> declaredInputs = new HashSet<>();
	private final Set<String> declaredOutputs = new HashSet<>();
	private final Set<String> declaredClocks = new HashSet<>();
	private final Map<String, Set<String>> buffers = new HashMap<>();
	private final Map<String, List<BlifGate>> consumers = new HashMap<>();
	private final Map<String, BlifGate> driver = new HashMap<>();
	private final List<BlifGate> gates = new ArrayList<>();
	private final String name;
	private final SourceLocation sloc;

	BlifModel(final String name, final SourceLocation sloc) {
		this.name = name;
		this.sloc = sloc;
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
		declaredInputs.addAll(names);
	}

	public Set<String> getDeclaredInputs() {
		return declaredInputs;
	}

	public void addOutputs(final List<String> names) {
		declaredOutputs.addAll(names);
	}

	public Set<String> getDeclaredOutputs() {
		return declaredOutputs;
	}

	public void addClocks(final List<String> names) {
		declaredInputs.addAll(names);
		declaredClocks.addAll(names);
	}

	public Set<String> getDeclaredClocks() {
		return declaredClocks;
	}

	public void addBuffers(final String bufferType, final List<String> names) {
		if (!buffers.containsKey(bufferType))
			buffers.put(bufferType, new HashSet<>());
		buffers.get(bufferType).addAll(names);
	}

	public Map<String, Set<String>> getBuffers() {
		return buffers;
	}

	public String getName() {
		return name;
	}

	public SourceLocation getSourceLocation() {
		return sloc;
	}
}
