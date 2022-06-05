package xsynth.xnf;

import java.util.LinkedHashMap;
import java.util.Map;

import xsynth.naming.Name;

class XnfPin {
	private final PinDirection dir;
	private final String pin;
	private final Name signal;
	private final Map<String, String> params;
	private final boolean invert;

	XnfPin(final PinDirection dir, final String pin, final boolean invert, final Name signal,
			final Map<String, String> params) {
		this.dir = dir;
		this.pin = pin;
		this.invert = invert;
		this.signal = signal;
		this.params = new LinkedHashMap<>(params != null ? params : Map.of());
	}

	public PinDirection getDir() {
		return dir;
	}

	public String getPin() {
		return pin;
	}

	public boolean isInvert() {
		return invert;
	}

	public Name getSignal() {
		return signal;
	}

	public Map<String, String> getParams() {
		return params;
	}
}