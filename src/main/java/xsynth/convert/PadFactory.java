package xsynth.convert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.SourceLocation;
import xsynth.blif.CustomGate;
import xsynth.blif.CustomGateFactory;
import xsynth.naming.Name;
import xsynth.naming.Namespace;
import xsynth.naming.SpecialName;
import xsynth.xnf.PadType;
import xsynth.xnf.PinDirection;
import xsynth.xnf.XnfGate;
import xsynth.xnf.XnfNetlist;

public class PadFactory implements CustomGateFactory {
	private static final String SPEED_FAST = "fast";
	private static final String SPEED_SLOW = "slow";
	private static final String SPEED_MEDFAST = "medfast";
	private static final String SPEED_MEDSLOW = "medslow";
	private static final List<String> SPEEDS = List.of(SPEED_FAST, SPEED_SLOW, SPEED_MEDFAST, SPEED_MEDSLOW);
	private static final String LEVEL_TTL = "ttl";
	private static final String LEVEL_CMOS = "cmos";
	private static final List<String> LEVELS = List.of(LEVEL_TTL, LEVEL_CMOS);
	private static final String IMPED_CAP = "cap";
	private static final String IMPED_RES = "res";
	private static final List<String> IMPEDS = List.of(IMPED_CAP, IMPED_RES);
	private static final String DELAY_NONE = "nodelay";
	private static final String DELAY_DEFAULT = "delay";

	private final List<String> flags = new ArrayList<>();

	public PadFactory(final boolean hasFastSlow, final boolean hasMedSpeed, final boolean hasNoDelay,
			final boolean hasDriverType) {
		if (hasFastSlow) {
			flags.add(SPEED_FAST);
			flags.add(SPEED_SLOW);
		}
		if (hasMedSpeed) {
			flags.add(SPEED_MEDFAST);
			flags.add(SPEED_MEDSLOW);
		}
		if (hasNoDelay) {
			flags.add(DELAY_NONE);
			flags.add(DELAY_DEFAULT); // for completeness; not actually passed on
		}
		if (hasDriverType) {
			// TODO could support input=TTL, output=CMOS case ("ttlcmos"?)
			flags.add(LEVEL_TTL);
			flags.add(LEVEL_CMOS);
			flags.add(IMPED_CAP);
			flags.add(IMPED_RES);
		}
	}

	@Override
	public List<String> getInputs() {
		return List.of("o", "t");
	}

	@Override
	public List<String> getOutputs() {
		return List.of("i");
	}

	@Override
	public List<String> getFlags() {
		return flags;
	}

	@Override
	public List<String> getRequiredSignals() {
		return List.of();
	}

	@Override
	public CustomGate newInstance(final Diagnostics diag, final SourceLocation sloc, final String loc,
			final List<String> flags, final Map<String, String> outputs, final Map<String, String> inputs)
			throws AbortedException {
		final boolean input = outputs.containsKey("i");
		final boolean output = inputs.containsKey("o");
		final boolean tristate = inputs.containsKey("t");
		if (!input && !output && !tristate)
			throw diag.error(sloc, "unconnected IO pad " + loc);
		if (tristate && !output)
			// tristate control is specified, but output level isn't. consistently assuming
			// unconnected stuff as zeros, that gives an open-drain output.
			// it's somewhat stupid though, so warn about it. the cleaner solution is to
			// just connect the signal to both pins (o and t) for an open-drain output. or
			// to explicitly connect o=GND; all that takes is a ".names GND" to define it.
			diag.warn(sloc, "tristate but no output value, creating an open drain output");

		final List<String> xflags = new ArrayList<>();
		setFlags(xflags, "speed", flags, SPEEDS);
		setFlags(xflags, "level", flags, LEVELS);
		setFlags(xflags, "impedance", flags, IMPEDS);

		// delay is different: there is NODELAY, but no corresponding DELAY. also, it
		// has to be specified on the IBUF, not on the EXT record.
		if (flags.contains(DELAY_DEFAULT) && flags.contains(DELAY_NONE))
			throw diag.error(sloc, "inconsistent delay flags: delay and nodelay");
		final boolean nodelay = flags.contains(DELAY_NONE);

		return new Pad(loc, xflags, inputs, outputs, nodelay);
	}

	private void setFlags(final List<String> xflags, final String name, final List<String> flags,
			final List<String> list) {
		final String[] specifiedFlags = flags.stream().filter(list::contains).toArray(n -> new String[n]);
		if (specifiedFlags.length > 1)
			throw new IllegalArgumentException("multiple " + name + " flags: " + Arrays.toString(specifiedFlags));
		if (specifiedFlags.length == 1)
			xflags.add(specifiedFlags[0].toUpperCase(Locale.ROOT));
	}

	private class Pad extends XnfCustomGate {
		private final String loc;
		private final Map<String, String> nodelay;

		public Pad(final String loc, final List<String> flags, final Map<String, String> inputs,
				final Map<String, String> outputs, final boolean nodelay) {
			super(CustomGateFactory.IOPAD_GATE, flags, outputs, inputs);
			this.loc = loc;
			this.nodelay = new LinkedHashMap<>();
			if (nodelay)
				this.nodelay.put("NODELAY", null);
		}

		@Override
		public void implement(final XnfNetlist xnf, final Namespace ns, final Map<String, Name> outputs,
				final Map<String, Name> inputs) {
			final Name input = outputs.get("i");
			Name output = inputs.get("o");
			final Name tristate = inputs.get("t");
			// base pad name on driver is possible, else on consumer. (note that an input
			// pad is a driver of the internal net)
			Name basename = input;
			if (basename == null)
				basename = output;
			if (basename == null)
				basename = tristate;
			final Name ext = basename.getAnonymous("PAD");
			// actually implement the "no output plus tristate = open drain" we warned about
			// above
			if (output == null && tristate != null)
				output = ns.getSpecial(SpecialName.GND);

			// always create a bidirectional pad. XACTstep will figure out the pad type from
			// the input and/or output buffers that are connected to it.
			xnf.addPad(PadType.BIDIRECTIONAL, ext, loc, null, flags);
			if (input != null) {
				final XnfGate gate = xnf.addSymbol("IBUF", nodelay);
				gate.connect(PinDirection.CONSUMER, "I", false, ext, null);
				gate.connect(PinDirection.DRIVER, "O", false, input, null);
			}
			if (output != null) {
				final XnfGate obuf;
				if (tristate != null) {
					// only create OBUFTs for pads that actually had tristate connected. XACTstep
					// emits a warning if tristate ends up being always active, and that warning
					// could be very confusing if tristate is apparently not even connected in the
					// original design.
					obuf = xnf.addSymbol("OBUFT", null);
					obuf.connect(PinDirection.CONSUMER, "T", false, tristate, null);
				} else
					obuf = xnf.addSymbol("OBUF", null);
				obuf.connect(PinDirection.DRIVER, "O", false, ext, null);
				obuf.connect(PinDirection.CONSUMER, "I", false, output, null);
			}
		}
	}
}
