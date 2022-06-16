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
	private static final String SPEED_FAST = "FAST";
	private static final String SPEED_SLOW = "SLOW";
	private static final String SPEED_MEDFAST = "MEDFAST";
	private static final String SPEED_MEDSLOW = "MEDSLOW";
	private static final List<String> SPEEDS = List.of(SPEED_FAST, SPEED_SLOW, SPEED_MEDFAST, SPEED_MEDSLOW);
	private static final String LEVEL_TTL = "TTL";
	private static final String LEVEL_CMOS = "CMOS";
	private static final String LEVEL_TTL_CMOS = "TTLCMOS";
	private static final List<String> LEVELS = List.of(LEVEL_TTL, LEVEL_CMOS, LEVEL_TTL_CMOS);
	private static final String IMPED_CAP = "CAP";
	private static final String IMPED_RES = "RES";
	private static final List<String> IMPEDS = List.of(IMPED_CAP, IMPED_RES);
	private static final String DELAY_NONE = "NODELAY";
	private static final String DELAY_DEFAULT = "DELAY";
	private static final String RESISTOR_PULLUP = "PULLUP";
	private static final String RESISTOR_PULLDOWN = "PULLDOWN";

	private final List<String> flags = new ArrayList<>();

	public PadFactory(final SlewRateControl slewRateControl, final Resistors resistors, final boolean hasNoDelay,
			final boolean hasDriverType) {
		flags.addAll(slewRateControl.flags);
		flags.addAll(resistors.flags);
		if (hasNoDelay) {
			flags.add(DELAY_NONE);
			flags.add(DELAY_DEFAULT); // for completeness; not actually passed on
		}
		if (hasDriverType) {
			flags.addAll(LEVELS);
			flags.addAll(IMPEDS);
		}
	}

	@Override
	public List<String> getInputs() {
		return List.of("O", "T");
	}

	@Override
	public List<String> getOutputs() {
		return List.of("I");
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
		final boolean input = outputs.containsKey("I");
		final boolean output = inputs.containsKey("O");
		final boolean tristate = inputs.containsKey("T");
		if (!input && !output && !tristate)
			throw diag.error(sloc, "unconnected IO pad " + loc);
		if (tristate && !output)
			// tristate control is specified, but output level isn't. consistently assuming
			// unconnected stuff as zeros, that gives an open-drain output.
			// it's somewhat stupid though, so warn about it. the cleaner solution is to
			// just connect the signal to both pins (o and t) for an open-drain output. or
			// to explicitly connect o=GND; all that takes is a ".names GND" to define it.
			diag.warn(sloc, "tristate but no output value, creating an open drain output");
		if (input && output && !tristate)
			// input on an output that is always enabled. this means the input always reads
			// the output value, and probably isn't what the user intended.
			diag.warn(sloc, "input on non-tristate output will always read back the output value");

		final List<String> padflags = new ArrayList<>();
		setFlags(padflags, "speed", flags, SPEEDS);
		setFlags(padflags, "impedance", flags, IMPEDS);

		// delay is different: there is NODELAY, but no corresponding DELAY. also, it
		// has to be specified on the IBUF, not on the EXT record.
		if (flags.contains(DELAY_DEFAULT) && flags.contains(DELAY_NONE))
			throw diag.error(sloc, "inconsistent delay flags: delay and nodelay");
		final Map<String, String> iflags = new LinkedHashMap<>();
		if (flags.contains(DELAY_NONE))
			iflags.put("NODELAY", null);
		final Map<String, String> oflags = new LinkedHashMap<>();

		// level flags go on the IBUF / OBUF, not on the pad
		final String[] levelFlags = flags.stream().filter(LEVELS::contains).toArray(n -> new String[n]);
		if (levelFlags.length > 1)
			throw diag.error(sloc, "inconsistent level flags: " + Arrays.toString(levelFlags));
		if (levelFlags.length > 0) {
			final String level = levelFlags[0];
			if (level.equals(LEVEL_TTL_CMOS)) {
				iflags.put(LEVEL_TTL, null);
				oflags.put(LEVEL_CMOS, null);
			} else {
				iflags.put(level, null);
				oflags.put(level, null);
			}
		}

		// resistors are placed as a component, not specified as a flag
		if (flags.contains(RESISTOR_PULLDOWN) && flags.contains(RESISTOR_PULLUP))
			throw diag.error(sloc, "inconsistent resistor flags: pullup and pulldown");
		final String resistor;
		if (flags.contains(RESISTOR_PULLDOWN))
			resistor = RESISTOR_PULLDOWN;
		else if (flags.contains(RESISTOR_PULLUP))
			resistor = RESISTOR_PULLUP;
		else
			resistor = null;

		return new Pad(loc, outputs, inputs, padflags, resistor, iflags, oflags);
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
		private final Map<String, String> iflags, oflags;
		private final String resistor;

		public Pad(final String loc, final Map<String, String> outputs, final Map<String, String> inputs,
				final List<String> flags, final String resistor, final Map<String, String> iflags,
				final Map<String, String> oflags) {
			super(null, outputs, inputs, flags, Map.of());
			this.loc = loc;
			this.resistor = resistor;
			this.iflags = iflags;
			this.oflags = oflags;
		}

		@Override
		public void implement(final XnfNetlist xnf, final Namespace ns, final Map<String, Name> outputs,
				final Map<String, Name> inputs) {
			final Name input = outputs.get("I");
			Name output = inputs.get("O");
			final Name tristate = inputs.get("T");
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
			// add pullup/pulldown resistors. some chips don't accept a pullup on an output
			// that cannot be tristated, but that can be left to XACTstep to check.
			// note the special direction "pullup" to avoid it being named according to its
			// output signal. that signal can also be driven by the IBUF, so the gate name
			// wouldn't be unique
			if (resistor != null) {
				final XnfGate gate = xnf.addSymbol(resistor.toUpperCase(Locale.ROOT), null);
				gate.connect(PinDirection.PULLUP, "O", false, ext, null);
				gate.allocateName(); // to generate a unique name derived from its almost-output
			}
			// create suitable IO buffers. if a pin is input-only or output-only, XACTstep
			// will report that, so no point in detecting that here
			if (input != null) {
				final XnfGate gate = xnf.addSymbol("IBUF", iflags);
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
					obuf = xnf.addSymbol("OBUFT", oflags);
					obuf.connect(PinDirection.CONSUMER, "T", false, tristate, null);
				} else
					obuf = xnf.addSymbol("OBUF", oflags);
				obuf.connect(PinDirection.DRIVER, "O", false, ext, null);
				obuf.connect(PinDirection.CONSUMER, "I", false, output, null);
			}
		}
	}

	public enum SlewRateControl {
		NONE, COARSE(SPEED_FAST, SPEED_SLOW), FINE(SPEED_FAST, SPEED_MEDFAST, SPEED_MEDSLOW, SPEED_SLOW);

		private List<String> flags;

		private SlewRateControl(final String... speeds) {
			flags = List.of(speeds);
		}
	}

	public enum Resistors {
		NONE, PULLUP_ONLY(RESISTOR_PULLUP), PULLUP_PULLDOWN(RESISTOR_PULLUP, RESISTOR_PULLDOWN);

		private List<String> flags;

		private Resistors(final String... resistors) {
			flags = List.of(resistors);
		}
	}
}
