package xsynth.naming;

import java.util.regex.Pattern;

public class SpecialName extends Numbered {
	/** Logic 1. */
	public static final String VCC = "VCC";
	/** Logic 0. */
	public static final String GND = "GND";
	/** The implicit global clock used for flipflops with unspecified clock. */
	public static final String GCLK = "GCLK";

	private static final Pattern NAME = Pattern.compile("[A-Z0-9]+");

	private final String name;

	SpecialName(final String name) {
		if (!NAME.matcher(name).matches())
			throw new IllegalArgumentException("illegal name " + name);
		this.name = name;
	}

	@Override
	public String getQualified(final int n) {
		return name + (n == 0 ? "" : "_" + String.valueOf(n));
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		return this == obj;
	}

	@Override
	public String toString() {
		return "SpecialName<" + name + ">";
	}
}
