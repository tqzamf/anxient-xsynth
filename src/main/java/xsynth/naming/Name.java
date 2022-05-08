package xsynth.naming;

public abstract class Name {
	private String xnf;

	public String getXnf() {
		return xnf;
	}

	protected String setXnf(final String xnf) {
		return this.xnf = xnf;
	}

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object obj);
}
