package xsynth.xnf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xsynth.naming.Name;

public class XnfGate {
	private final List<XnfPin> pins = new ArrayList<>();
	private final Map<String, String> params;
	private final String type;
	private Name name;

	public XnfGate(final String type, final Map<String, String> params) {
		this.params = new HashMap<>(params != null ? params : Map.of());
		this.type = type;
	}

	public void connect(final PinDirection dir, final String pin, final boolean invert, final Name signal,
			final Map<String, String> params) {
		pins.add(new XnfPin(dir, pin, invert, signal, params));
	}

	public Name getName() {
		if (name == null)
			name = computeName();
		return name;
	}

	private Name computeName() {
		// derive block name from the net it drives
		for (final XnfPin pin : pins)
			if (pin.getDir() == PinDirection.DRIVER)
				return pin.getSignal();
		// in the rare case that a block doesn't drive anything (basically only RDCLK
		// and the special output pads), use a name derived from its first input
		return pins.get(0).getSignal().getAnonymous("SYM");
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
