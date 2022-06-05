package xsynth.blif;

import java.util.List;
import java.util.Map;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.SourceLocation;

public interface CustomGateFactory {
	/**
	 * name of the {@link CustomGateFactory} internally used to implement IO pads
	 * ({@code .pad}). shouldn't be used as a .gate because then the pad is
	 * generated with {@code LOC=iopad}, which XACTstep probably doesn't like.
	 */
	public static final String IOPAD_GATE = "iopad";
	/** name of the {@code .gate} used to specify the implicit global gate clock */
	public static final String LATCH_CLOCK_GATE = "latchclock";

	public CustomGate newInstance(final Diagnostics diag, SourceLocation sloc, final String name,
			final List<String> flags, final Map<String, String> outputs, final Map<String, String> inputs)
			throws AbortedException;

	public List<String> getInputs();

	public List<String> getOutputs();

	public List<String> getFlags();

	public List<String> getRequiredSignals();
}
