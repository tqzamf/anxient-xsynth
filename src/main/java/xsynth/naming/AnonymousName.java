package xsynth.naming;

import java.util.regex.Pattern;

class AnonymousName extends Numbered {
	private static final Pattern QUALIFIER = Pattern.compile("[A-Z0-9_]+");

	private final Name base;
	private final String qualifier;

	AnonymousName(final Namespace ns, final Name base, final String qualifier) {
		super(ns);
		if (base == null)
			throw new NullPointerException("base name is null");
		if (qualifier == null)
			throw new NullPointerException("qualifier is null");
		if (!QUALIFIER.matcher(qualifier).matches())
			throw new IllegalArgumentException("illegal qualifier " + qualifier);

		this.base = base;
		this.qualifier = qualifier;
	}

	@Override
	public String getQualified(final int n) {
		return base.getXnf() + "/" + qualifier + (n == 0 ? "" : String.valueOf(n));
	}

	@Override
	public int hashCode() {
		return 31 * base.hashCode() + qualifier.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		return this == obj;
	}

	@Override
	public String toString() {
		return "AnonymousName[" + base + " / " + qualifier + "]";
	}
}
