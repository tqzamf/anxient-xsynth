package xsynth.chips;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.SourceLocation;
import xsynth.convert.SpecialGateFactory;
import xsynth.convert.XnfCustomGate;
import xsynth.naming.Name;
import xsynth.naming.Namespace;
import xsynth.xnf.PinDirection;
import xsynth.xnf.XnfGate;
import xsynth.xnf.XnfNetlist;

public class ReadbackFactory extends SpecialGateFactory {
	public ReadbackFactory() {
		super(List.of("DATA", "RIP"), List.of("TRIG", "CLK"), List.of(), List.of(),
				Map.of("TRIG", "MD0", "DATA", "MD1"));
	}

	@Override
	public XnfCustomGate newInstance(final Diagnostics diag, final SourceLocation sloc, final String name,
			final List<String> flags, final Map<String, String> outputs, final Map<String, String> inputs)
			throws AbortedException {
		return new SpecialGate(name, outputs, inputs) {
			@Override
			public void implement(final XnfNetlist xnf, final Namespace ns, final Map<String, Name> outputs,
					final Map<String, Name> inputs) {
				// readback speciality: CLK defaults to CCLK, and is connected to to a separate
				// RDCLK, not to the main RDBK symbol
				final Map<String, Name> rdbkInputs = new LinkedHashMap<>(inputs);
				final Name clock = rdbkInputs.remove("CLK");
				super.implement(xnf, ns, outputs, rdbkInputs);

				if (clock != null) {
					final XnfGate gate = xnf.addSymbol("RDCLK", null);
					gate.connect(PinDirection.CONSUMER, "I", false, clock, null);
					gate.allocateName();
				}
			}
		};
	}
}
