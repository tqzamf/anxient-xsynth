package xsynth.xnf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import xsynth.naming.Name;

public class XnfGate {
	private final List<XnfPin> pins = new ArrayList<>();
	private final Map<String, String> params;
	private final String type;
	private Name name;

	public XnfGate(final String type, final Map<String, String> params) {
		this.params = new LinkedHashMap<>(params != null ? params : Map.of());
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

	/**
	 * must be called to assign a name to gates that don't drive any nets. dates
	 * without outputs cannot be named after their output net because there is none,
	 * and they cannot be named after their input nets either because those names
	 * are already in use by the drivers of those nets.
	 */
	public void allocateName() {
		if (name != null)
			throw new IllegalStateException("allocateName() has already beed called for " + name);
		for (final XnfPin pin : pins)
			if (pin.getDir() == PinDirection.DRIVER)
				throw new IllegalStateException("allocateName() on gate which drives " + pin.getSignal());
		name = pins.get(0).getSignal().getAnonymous("SYM");
	}

	private Name computeName() {
		// derive block name from the net it drives
		for (final XnfPin pin : pins)
			if (pin.getDir() == PinDirection.DRIVER)
				return pin.getSignal();
		throw new IllegalStateException("allocateName() has not beed called for " + name);
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
