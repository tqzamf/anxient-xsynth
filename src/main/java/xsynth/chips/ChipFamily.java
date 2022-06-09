package xsynth.chips;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

import xsynth.blif.CustomGateFactory;
import xsynth.convert.GlobalClockFactory;
import xsynth.convert.PadFactory;
import xsynth.convert.PadFactory.Resistors;
import xsynth.convert.PadFactory.SlewRateControl;

public class ChipFamily {
	public static final List<ChipFamily> FAMILIES = List.of(new XC2000Family(), new XC3000Family(), new XC5200Family());

	protected final Map<String, CustomGateFactory> customGates = new LinkedHashMap<>();
	protected final Set<String> bufferTypes = new HashSet<>();
	private final Pattern pattern;
	private final int maxGateInputs;
	private final boolean hasLatches;
	private final boolean hasLatchInitValue;

	protected ChipFamily(final String name, final String regex, final int maxGateInputs, final boolean hasLatches,
			final boolean hasLatchInitValue, final SlewRateControl slewRateControl, final Resistors resistors,
			final boolean hasNoDelay, final boolean hasDriverType) {
		this.maxGateInputs = maxGateInputs;
		this.hasLatches = hasLatches;
		this.hasLatchInitValue = hasLatchInitValue;
		customGates.put(CustomGateFactory.IOPAD_GATE,
				new PadFactory(slewRateControl, resistors, hasNoDelay, hasDriverType));
		customGates.put(CustomGateFactory.LATCH_CLOCK_GATE, new GlobalClockFactory());
		pattern = Pattern.compile("^" + regex + ".*", Pattern.CASE_INSENSITIVE);
		bufferTypes.add("BUFG"); // supported by all chips
	}

	public boolean matches(final String part) {
		return pattern.matcher(part).matches();
	}

	public Map<String, CustomGateFactory> getCustomGates() {
		return customGates;
	}

	public Set<String> getBufferTypes() {
		return bufferTypes;
	}

	public int getMaxGateInputs() {
		return maxGateInputs;
	}

	public boolean hasLatches() {
		return hasLatches;
	}

	public boolean hasLatchInitValue() {
		return hasLatchInitValue;
	}

	public static ChipFamily forPart(final String part) throws NoSuchElementException {
		for (final ChipFamily f : FAMILIES)
			if (f.matches(part))
				return f;
		throw new NoSuchElementException("no family matching part name " + part);
	}
}
