package xsynth.xnf;

public enum PinDirection {
	DRIVER("O"), CONSUMER("I");

	private final String code;

	private PinDirection(final String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
