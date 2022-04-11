package blif2xnf.naming;

import java.util.regex.Pattern;

class Anonymous implements Name {
	private static final Pattern QUALIFIER = Pattern.compile("[A-Z]+");

	private final Name base;
	private final String qualifier;

	private String xnf;

	Anonymous(final Global base, final String qualifier) {
		if (!QUALIFIER.matcher(qualifier).matches())
			throw new IllegalArgumentException("illegal qualifier " + qualifier);

		this.base = base;
		this.qualifier = qualifier;
	}

	public String getQualified(final int n) {
		return base.getXnf() + "/" + qualifier + (n == 0 ? "" : String.valueOf(n));
	}

	@Override
	public String getXnf() {
		return xnf;
	}

	String setXnf(final String xnf) {
		return this.xnf = xnf;
	}
}
