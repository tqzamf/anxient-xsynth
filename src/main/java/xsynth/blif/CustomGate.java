package xsynth.blif;

import java.util.Collection;
import java.util.Map;

public class CustomGate implements BlifGate {
	protected final Map<String, String> inputs;
	protected final Map<String, String> outputs;

	public CustomGate(final Map<String, String> outputs, final Map<String, String> inputs) {
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
