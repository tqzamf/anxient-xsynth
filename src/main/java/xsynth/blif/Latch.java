package xsynth.blif;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Latch implements BlifGate {
	public static final List<String> LATCH_TYPES = Stream.of(LatchType.values()).map(LatchType::name)
			.collect(Collectors.toUnmodifiableList());
	public static final List<String> INITIAL_VALUES = Stream.of(LatchInitialValue.values())
			.map(iv -> Integer.toString(iv.ordinal())).collect(Collectors.toUnmodifiableList());

	private final String inputName;
	private final LatchType type;
	private final String clockName;
	private final LatchInitialValue initialValue;
	private final String outputName;

	public Latch(final String outputName, final String inputName, final LatchType type, final String clockName,
			final LatchInitialValue initialValue) {
		this.outputName = outputName;
		this.inputName = inputName;
		this.type = type;
		this.clockName = clockName;
		this.initialValue = initialValue;
	}

	public Latch(final String outputName, final String inputName, final String type, final String clockName,
			final String initialValue) throws IllegalArgumentException {
		if (type == null && clockName != null || type != null && clockName == null)
			throw new NullPointerException("type and clock name must be either both null or both non-null");

		this.outputName = outputName;
		this.inputName = inputName;

		if (type != null)
			try {
				this.type = LatchType.valueOf(type);
			} catch (final IllegalArgumentException e) {
				throw new IllegalArgumentException("invalid latch type " + type);
			}
		else
			this.type = LatchType.re;
		this.clockName = clockName;

		if (initialValue != null)
			try {
				this.initialValue = LatchInitialValue.values()[Integer.parseInt(initialValue)];
			} catch (final NumberFormatException | ArrayIndexOutOfBoundsException e) {
				throw new IllegalArgumentException("invalid latch initial value " + initialValue);
			}
		else
			this.initialValue = LatchInitialValue.UNKNOWN;
	}

	@Override
	public List<String> getOutputs() {
		return List.of(outputName);
	}

	@Override
	public List<String> getInputs() {
		if (clockName == null)
			return List.of(inputName);
		return List.of(inputName, clockName);
	}

	public String getDataInput() {
		return inputName;
	}

	public String getDataOutput() {
		return outputName;
	}

	public String getClockInput() {
		return clockName;
	}

	public LatchType getType() {
		return type;
	}

	public LatchInitialValue getInitialValue() {
		return initialValue;
	}

	@Override
	public String toString() {
		return "Latch[" + inputName + ", " + outputName + ", " + type + ", " + clockName + ", " + initialValue + "]";
	}
}
