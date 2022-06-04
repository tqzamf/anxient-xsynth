package xsynth.xnf;

public enum PadType {
	INPUT("I"), OUTPUT("O"), TRISTATE("T"), BIDIRECTIONAL("B"), UNBONDED("U");

	private final String code;

	private PadType(final String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
