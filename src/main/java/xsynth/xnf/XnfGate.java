package xsynth.xnf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xsynth.naming.Name;

public class XnfGate  {
	private final List<XnfPin> pins = new ArrayList<>();
	private final Name name;
	private final Map<String, String> params;
	private final String type;

	public XnfGate(final String type, final Name name, final Map<String, String> params) {
		this.name = name;
		this.params = new HashMap<>(params != null ? params : Map.of());
		this.type = type;
	}

	public void connect(final PinDirection dir, final String pin, final boolean invert, final Name signal,
			final Map<String, String> params) {
		pins.add(new XnfPin(dir, pin, invert, signal, params));
	}

	public Name getName() {
		return name;
	}

	public Map<String, String> getParams() {
		return params;
	}

	public List<XnfPin> getPins() {
		return pins;
	}

	public String getType() {
		return type;
	}
}
