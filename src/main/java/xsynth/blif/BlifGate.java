package xsynth.blif;

import java.util.Collection;

public interface BlifGate {
	public Collection<String> getOutputs();

	public Collection<String> getInputs();
}
