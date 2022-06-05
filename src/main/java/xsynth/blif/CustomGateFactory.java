package xsynth.blif;

import java.util.List;
import java.util.Map;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.SourceLocation;

public interface CustomGateFactory {
	public static final String IOPAD_GATE = "iopad";

	public CustomGate newInstance(final Diagnostics diag, SourceLocation sloc, final String name,
			final List<String> flags, final Map<String, String> outputs, final Map<String, String> inputs)
			throws AbortedException;

	public List<String> getInputs();

	public List<String> getOutputs();

	public List<String> getFlags();

	public List<String> getRequiredSignals();
}
