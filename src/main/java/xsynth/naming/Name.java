package xsynth.naming;

public abstract class Name {
	private final Namespace ns;
	private String xnf;

	public Name(final Namespace ns) {
		this.ns = ns;
	}

	public Name getAnonymous(final String qualifier) {
		return ns.getAnonymous(this, qualifier);
	}

	public String getXnf() {
		return xnf;
	}

	protected void setXnf(final String xnf) {
		this.xnf = xnf;
	}

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object obj);
}
