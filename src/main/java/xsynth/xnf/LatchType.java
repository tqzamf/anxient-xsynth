package xsynth.xnf;

public enum LatchType {
	FLIPFLOP("DFF", "C"), LATCH("DLAT", "G");

	private final String symbol;
	private final String clockPin;

	private LatchType(final String symbol, final String clockPin) {
		this.symbol = symbol;
		this.clockPin = clockPin;
	}

	public String getSymbol() {
		return symbol;
	}

	public String getClockPin() {
		return clockPin;
	}
}
