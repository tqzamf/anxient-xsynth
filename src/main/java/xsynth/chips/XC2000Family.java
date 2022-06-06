package xsynth.chips;

import java.util.List;
import java.util.Map;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.SourceLocation;
import xsynth.convert.PadFactory.Resistors;
import xsynth.convert.PadFactory.SlewRateControl;
import xsynth.convert.SpecialGateFactory;
import xsynth.convert.XnfCustomGate;
import xsynth.naming.Namespace;
import xsynth.xnf.XnfNetlist;

public class XC2000Family extends ChipFamily {
	public XC2000Family() {
		super("XC2000", "20[0-9]{2}", 4, true, false, SlewRateControl.NONE, Resistors.NONE, false, false);
		bufferTypes.add("ACLK");
		bufferTypes.add("GCLK");
		customGates.put("OSC", new OSC());
	}

	private static class OSC extends SpecialGateFactory {
		public OSC() {
			super(List.of("O"), List.of(), List.of("O"));
		}

		@Override
		public XnfCustomGate newInstance(final Diagnostics diag, final SourceLocation sloc, final String name,
				final List<String> flags, final Map<String, String> outputs, final Map<String, String> inputs)
				throws AbortedException {
			return new SpecialGate(name, outputs, inputs) {
				@Override
				public void implement(final XnfNetlist xnf, final Namespace ns, final BufferProvider buffers) {
					// in XC2000 family chips, OSC output must be connected to ACLK input, so we
					// force it to use an ACLK buffer.
					super.implement(xnf, ns, (name, forceBuffer) -> buffers.getBufferedOutput(name, "ACLK"));
				}
			};
		}
	}
}
