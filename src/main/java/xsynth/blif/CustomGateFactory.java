package xsynth.blif;

import java.util.List;
import java.util.Map;

public interface CustomGateFactory {
	public static final String IOPAD_GATE = "iopad";

	public CustomGate newInstance(final String name, final List<String> flags, final Map<String, String> inputs,
			final Map<String, String> outputs) throws IllegalArgumentException;

	public List<String> getInputs();

	public List<String> getOutputs();

	public List<String> getFlags();

	public List<String> getRequiredSignals();
}
