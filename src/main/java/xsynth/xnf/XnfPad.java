package xsynth.xnf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xsynth.naming.Name;

public class XnfPad {
	private final Name signal;
	private final Map<String, String> params;
	private final PadType type;
	private final String loc;
	private final List<String> flags;

	public XnfPad(final PadType type, final Name signal, final String loc, final Map<String, String> params,
			final List<String> flags) {
		this.signal = signal;
		this.params = new HashMap<>(params != null ? params : Map.of());
		this.type = type;
		this.loc = loc;
		this.flags = new ArrayList<>(flags != null ? flags : List.of());
	}

	public Name getSignal() {
		return signal;
	}

	public Map<String, String> getParams() {
		return params;
	}

	public PadType getType() {
		return type;
	}

	public List<String> getFlags() {
		return flags;
	}

	public String getLoc() {
		return loc;
	}
}
