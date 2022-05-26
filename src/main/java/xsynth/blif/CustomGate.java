package xsynth.blif;

import java.util.Collection;
import java.util.Map;

public class CustomGate implements BlifGate {
	private final Map<String, String> inputs;
	private final Map<String, String> outputs;

	public CustomGate(final Map<String, String> inputs, final Map<String, String> outputs) {
		this.inputs = inputs;
		this.outputs = outputs;
	}

	@Override
	public Collection<String> getOutputs() {
		return outputs.values();
	}

	@Override
	public Collection<String> getInputs() {
		return inputs.values();
	}

	@Override
	public String toString() {
		return "CustomGate[" + getOutputs() + ", " + getInputs() + "]";
	}
}
