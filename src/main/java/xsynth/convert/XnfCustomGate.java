package xsynth.convert;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import xsynth.blif.CustomGate;
import xsynth.naming.Name;
import xsynth.naming.Namespace;
import xsynth.xnf.XnfNetlist;

public abstract class XnfCustomGate extends CustomGate {
	protected final List<String> flags;
	protected final Map<String, String> params;
	protected final String name;

	public XnfCustomGate(final String name, final Map<String, String> outputs, final Map<String, String> inputs,
			final List<String> flags, final Map<String, String> params) {
		super(outputs, inputs);
		this.name = name;
		this.flags = flags;
		this.params = params;
	}

	public XnfCustomGate(final String name, final Map<String, String> outputs, final Map<String, String> inputs) {
		this(name, outputs, inputs, List.of(), Map.of());
	}

	public void implement(final XnfNetlist xnf, final Namespace ns, final BufferProvider buffers) {
		final Map<String, Name> inputs = new LinkedHashMap<>();
		for (final String port : this.inputs.keySet())
			inputs.put(port, ns.getGlobal(this.inputs.get(port)));
		final Map<String, Name> outputs = new LinkedHashMap<>();
		for (final String port : this.outputs.keySet()) {
			final String signal = this.outputs.get(port);
			final Name gateOutput = buffers.getBufferedOutput(signal, null);
			outputs.put(port, gateOutput);
		}
		implement(xnf, ns, outputs, inputs);
	}

	protected abstract void implement(final XnfNetlist xnf, final Namespace ns, Map<String, Name> outputs,
			Map<String, Name> inputs);

	@FunctionalInterface
	public interface BufferProvider {
		public Name getBufferedOutput(final String name, final String forceBuffer);
	}
}
