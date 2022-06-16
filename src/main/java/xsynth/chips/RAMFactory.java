package xsynth.chips;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.SourceLocation;
import xsynth.blif.CustomGate;
import xsynth.convert.SpecialGateFactory;
import xsynth.naming.Name;
import xsynth.naming.Namespace;
import xsynth.naming.SpecialName;
import xsynth.xnf.XnfNetlist;

public class RAMFactory extends SpecialGateFactory {
	/** maximum bit width of a multi-bit RAM */
	private static final int MAX_BITS = 255;
	private final List<String> outputPrefixes;
	private final List<String> addressPrefixes;
	private final int numAddresses;
	private final boolean hasWriteClock;

	public RAMFactory(final List<String> outputPrefixes, final List<String> addressPrefixes,
			final boolean hasWriteClock, final int numAddresses) {
		super(buildOutputs(outputPrefixes), buildInputs(addressPrefixes, numAddresses, hasWriteClock),
				hasWriteClock ? List.of("WE", "WCLK") : List.of("WE"));
		this.outputPrefixes = outputPrefixes;
		this.addressPrefixes = addressPrefixes;
		this.hasWriteClock = hasWriteClock;
		this.numAddresses = numAddresses;
	}

	private static List<String> buildOutputs(final List<String> prefixes) {
		final List<String> outputs = new ArrayList<>();
		for (final String out : prefixes)
			for (int i = 0; i < MAX_BITS; i++)
				outputs.add(out + i); // On / SPOn / DPOn for multi-bit RAMs
		outputs.addAll(prefixes); // allow O / SPO / DPO for single-bit RAMs
		return outputs;
	}

	private static List<String> buildInputs(final List<String> prefixes, final int numAddresses,
			final boolean hasWriteClock) {
		final List<String> inputs = new ArrayList<>();
		for (final String addr : prefixes)
			for (int i = 0; i < numAddresses; i++)
				inputs.add(addr + i);
		if (hasWriteClock)
			inputs.add("WCLK");
		inputs.add("WE");
		for (int i = 0; i < 255; i++)
			inputs.add("D" + i); // Dn for multi-bit RAMs
		inputs.add("D"); // allow S for single-bit RAMs
		return inputs;
	}

	@Override
	public List<CustomGate> newInstance(final Diagnostics diag, final SourceLocation sloc, final String name,
			final List<String> flags, final Map<String, String> outputs, final Map<String, String> inputs)
			throws AbortedException {
		// determine width of RAM
		final List<String> bits = new ArrayList<>();
		for (int i = 0; i < 255; i++)
			if (bitExists(String.valueOf(i), outputs, inputs))
				bits.add(String.valueOf(i));
		final boolean hasUnnumbered = bitExists("", outputs, inputs);
		if (hasUnnumbered) {
			if (!bits.isEmpty())
				throw diag.error(sloc, "connection to both numbered and numbered ports!");
			bits.add("");
		}

		// determine depth of RAM (number of address pins) and collect address input
		// connections
		final List<String> groundedAddress = new ArrayList<>();
		final Map<String, String> globalInputs = new LinkedHashMap<String, String>();
		for (final String prefix : addressPrefixes) {
			int nextAddr = 0;
			for (int i = 0; i < numAddresses; i++) {
				final String addr = prefix + i;
				if (inputs.containsKey(addr)) {
					for (int a = nextAddr; a < i; a++)
						diag.warn(sloc, "unconnected address input, assuming zero: " + prefix + a);
					globalInputs.put(addr, inputs.get(addr));
					nextAddr = i + 1;
				} else
					groundedAddress.add(addr);
			}
		}
		// write control; these also connect to all bits in parallel
		globalInputs.put("WE", inputs.get("WE"));
		if (hasWriteClock)
			globalInputs.put("WCLK", inputs.get("WCLK"));

		// remove RAM bits that are write-only
		for (final Iterator<String> iter = bits.iterator(); iter.hasNext();) {
			final String bit = iter.next();
			if (!inputs.containsKey("D" + bit))
				diag.warn(sloc, "unconnected data input, assuming zero: D" + bit);
			boolean hasOutput = false;
			for (final String prefix : outputPrefixes)
				hasOutput |= outputs.containsKey(prefix + bit);
			if (!hasOutput) {
				diag.warn(sloc, "unconnected data output, removing write-only memory bit: "
						+ outputPrefixes.stream().map(prefix -> prefix + bit).collect(Collectors.joining(", ")));
				iter.remove();
			}
		}

		// generate RAM instances
		final List<CustomGate> gates = new ArrayList<>();
		for (final String bit : bits) {
			// collect pin assignments, renaming D15 to D for each instance
			final Map<String, String> inPorts = new LinkedHashMap<>(globalInputs);
			final String inName = "D" + bit;
			if (inputs.containsKey(inName))
				inPorts.put("D", inputs.get(inName));
			final Map<String, String> outPorts = new LinkedHashMap<>();
			for (final String prefix : outputPrefixes) {
				final String outName = prefix + bit;
				if (outputs.containsKey(outName))
					outPorts.put(prefix, outputs.get(outName));
			}
			// and at that point, the RAM bit behaves like any other gate, except that we
			// explicitly ground all unconnected address and data inputs
			gates.add(new SpecialGate(name, outPorts, inPorts) {
				@Override
				public void implement(final XnfNetlist xnf, final Namespace ns, final Map<String, Name> outputs,
						final Map<String, Name> inputs) {
					for (final String gnd : groundedAddress)
						inputs.put(gnd, ns.getSpecial(SpecialName.GND));
					if (!inputs.containsKey("D"))
						inputs.put("D", ns.getSpecial(SpecialName.GND));
					super.implement(xnf, ns, outputs, inputs);
				}
			});
		}
		return gates;
	}

	private boolean bitExists(final String bit, final Map<String, String> outputs, final Map<String, String> inputs) {
		if (inputs.containsKey("D" + bit))
			return true;
		for (final String prefix : outputPrefixes)
			if (outputs.containsKey(prefix + bit))
				return true;
		return false;
	}
}
