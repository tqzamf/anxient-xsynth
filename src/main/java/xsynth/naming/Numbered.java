package xsynth.naming;

abstract class Numbered extends Name {
	Numbered(final Namespace ns) {
		super(ns);
	}

	protected abstract String getQualified(int n);
}
