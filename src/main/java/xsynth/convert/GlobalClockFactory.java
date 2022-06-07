package xsynth.convert;

import java.util.List;
import java.util.Map;

import xsynth.Diagnostics;
import xsynth.SourceLocation;
import xsynth.blif.CustomGate;
import xsynth.blif.CustomGateFactory;
import xsynth.naming.Name;
import xsynth.naming.Namespace;
import xsynth.naming.SpecialName;
import xsynth.xnf.XnfNetlist;

public class GlobalClockFactory implements CustomGateFactory {
	public GlobalClockFactory() {
	}

	@Override
	public List<String> getInputs() {
		return List.of("C");
	}

	@Override
	public List<String> getOutputs() {
		return List.of();
	}

	@Override
	public List<String> getFlags() {
		return List.of();
	}

	@Override
	public List<String> getRequiredSignals() {
		return List.of("C");
	}

	@Override
	public CustomGate newInstance(final Diagnostics diag, final SourceLocation sloc, final String loc,
			final List<String> flags, final Map<String, String> outputs, final Map<String, String> inputs) {
		return new Pad(inputs, outputs);
	}

	private class Pad extends XnfCustomGate {
		public Pad(final Map<String, String> inputs, final Map<String, String> outputs) {
			super(null, outputs, inputs);
		}

		@Override
		public void implement(final XnfNetlist xnf, final Namespace ns, final Map<String, Name> outputs,
				final Map<String, Name> inputs) {
			xnf.addBuffer("BUF", ns.getSpecial(SpecialName.GCLK), inputs.get("C"));
		}
	}
}
