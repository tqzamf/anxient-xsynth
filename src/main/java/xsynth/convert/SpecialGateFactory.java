package xsynth.convert;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.SourceLocation;
import xsynth.blif.CustomGate;
import xsynth.blif.CustomGateFactory;
import xsynth.naming.Name;
import xsynth.naming.Namespace;
import xsynth.xnf.PinDirection;
import xsynth.xnf.XnfGate;
import xsynth.xnf.XnfNetlist;

public class SpecialGateFactory implements CustomGateFactory {
	private final List<String> flags;
	private final List<String> outputs;
	private final List<String> inputs;
	private final List<String> required;
	private final Map<String, String> specialInputs = new TreeMap<>();
	private final Map<String, String> specialOutputs = new TreeMap<>();
	private final boolean directConnect;

	public SpecialGateFactory(final List<String> outputs, final List<String> inputs, final List<String> required,
			final List<String> flags, final Map<String, String> specialPadConnections, final boolean directConnect) {
		this.outputs = outputs;
		this.inputs = inputs;
		this.required = required;
		this.flags = flags;
		this.directConnect = directConnect;
		for (final String special : specialPadConnections.keySet())
			(inputs.contains(special) ? specialInputs : specialOutputs).put(special,
					specialPadConnections.get(special));
	}

	public SpecialGateFactory(final List<String> outputs, final List<String> inputs, final List<String> required) {
		this(outputs, inputs, required, List.of(), Map.of(), true /* irrelevant */);
	}

	@Override
	public List<String> getInputs() {
		return inputs;
	}

	@Override
	public List<String> getOutputs() {
		return outputs;
	}

	@Override
	public List<String> getFlags() {
		return flags;
	}

	@Override
	public List<String> getRequiredSignals() {
		return required;
	}

	@Override
	public List<CustomGate> newInstance(final Diagnostics diag, final SourceLocation sloc, final String name,
			final List<String> flags, final Map<String, String> outputs, final Map<String, String> inputs)
			throws AbortedException {
		return List.of(new SpecialGate(name, outputs, inputs, flags, Map.of()));
	}

	protected class SpecialGate extends XnfCustomGate {
		public SpecialGate(final String name, final Map<String, String> outputs, final Map<String, String> inputs,
				final List<String> flags, final Map<String, String> params) {
			super(name, outputs, inputs, flags, params);
		}

		public SpecialGate(final String name, final Map<String, String> outputs, final Map<String, String> inputs) {
			super(name, outputs, inputs, List.of(), Map.of());
		}

		@Override
		public void implement(final XnfNetlist xnf, final Namespace ns, final Map<String, Name> outputs,
				final Map<String, Name> inputs) {
			final Map<String, String> flags = new LinkedHashMap<>(params);
			for (final String flag : this.flags)
				flags.put(flag, null);
			final XnfGate gate = xnf.addSymbol(name.toUpperCase(Locale.ROOT), flags);
			connectAll(xnf, ns, gate, PinDirection.CONSUMER, inputs, specialInputs);
			connectAll(xnf, ns, gate, PinDirection.DRIVER, outputs, specialOutputs);
			if (outputs.isEmpty() && specialOutputs.isEmpty())
				gate.allocateName();
		}

		private void connectAll(final XnfNetlist xnf, final Namespace ns, final XnfGate gate, final PinDirection dir,
				final Map<String, Name> connections, final Map<String, String> specialPad) {
			for (final String port : connections.keySet())
				gate.connect(dir, port, false, connections.get(port), null);

			// this is for ports like BSCAN's TDI or TDO which, if not connected, are
			// connected to a special pad symbol with a particular name.
			for (final String port : specialPad.keySet())
				if (!connections.containsKey(port)) {
					final XnfGate pad = xnf.addSymbol(specialPad.get(port), null);
					final Name wire = ns.getAnonymous(gate.getType() + "_" + port);
					gate.connect(dir, port, false, wire, null);
					final Name padWire;
					if (!directConnect) {
						padWire = wire.getAnonymous("PAD");
						if (dir == PinDirection.DRIVER)
							xnf.addBuffer("OBUF", padWire, wire);
						else
							xnf.addBuffer("IBUF", wire, padWire);
					} else
						padWire = wire;
					if (dir == PinDirection.DRIVER) {
						pad.connect(PinDirection.CONSUMER, "O", false, padWire, null);
						pad.allocateName();
					} else
						pad.connect(PinDirection.DRIVER, "I", false, padWire, null);
				}
		}
	}
}
