package xsynth.chips;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

import xsynth.blif.CustomGateFactory;
import xsynth.convert.GlobalClockFactory;
import xsynth.convert.PadFactory;

public class ChipFamily {
	public static final List<ChipFamily> FAMILIES = List.of(new XC2000Family(), new XC3000Family(), new XC5200Family());

	protected final Map<String, CustomGateFactory> customGates = new LinkedHashMap<>();
	private final Pattern pattern;
	private final int maxGateInputs;
	private final boolean hasLatches;
	private final boolean hasLatchInitValue;

	protected ChipFamily(final String name, final String regex, final int maxGateInputs, final boolean hasLatches,
			final boolean hasLatchInitValue, final boolean hasFastSlow, final boolean hasMedSpeed,
			final boolean hasNoDelay, final boolean hasDriverType) {
		this.maxGateInputs = maxGateInputs;
		this.hasLatches = hasLatches;
		this.hasLatchInitValue = hasLatchInitValue;
		customGates.put(CustomGateFactory.IOPAD_GATE,
				new PadFactory(hasFastSlow, hasMedSpeed, hasNoDelay, hasDriverType));
		customGates.put(CustomGateFactory.LATCH_CLOCK_GATE, new GlobalClockFactory());
		pattern = Pattern.compile("^(?:xc)?" + regex + ".*", Pattern.CASE_INSENSITIVE);
	}

	public boolean matches(final String part) {
		return pattern.matcher(part).matches();
	}

	public Map<String, CustomGateFactory> getCustomGates() {
		return customGates;
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
