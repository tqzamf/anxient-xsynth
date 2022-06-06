package xsynth.naming;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Namespace extends GlobalName {
	private final Map<String, SpecialName> specials;
	private final Map<String, GlobalName> globals = new LinkedHashMap<>();
	private final List<Numbered> derived = new ArrayList<>();
	private final Map<String, Namespace> namespaces = new LinkedHashMap<>();
	private final boolean qualifyAllNames;
	private final Namespace parent;
	private final Map<String, String> ports;

	public Namespace(final boolean qualifyAllNames) {
		super();
		this.qualifyAllNames = qualifyAllNames;
		specials = new LinkedHashMap<>();
		parent = null;
		ports = Map.of();
		setXnf("");
	}

	Namespace(final Namespace parent, final String name, final Map<String, String> ports) {
		super(parent, name, true);
		this.ports = ports;
		qualifyAllNames = parent.qualifyAllNames;
		specials = null;
		this.parent = parent;
	}

	public Name getGlobal(final String name) {
		if (ports.containsKey(name))
			return parent.getGlobal(ports.get(name));

		if (!globals.containsKey(name))
			globals.put(name, new GlobalName(this, name, false));
		return globals.get(name);
	}

	Name getAnonymous(final Name base, final String qualifier) {
		final AnonymousName name = new AnonymousName(this, base, qualifier);
		derived.add(name);
		return name;
	}

	@Override
	public Name getAnonymous(final String qualifier) {
		return this.getAnonymous(this, qualifier);
	}

	public Name getSpecial(final String name) {
		if (parent != null)
			return parent.getSpecial(name);

		if (!specials.containsKey(name)) {
			final SpecialName net = new SpecialName(this, name);
			specials.put(name, net);
			derived.add(net);
		}
		return specials.get(name);
	}

	public boolean hasSpecial(final String name) {
		if (parent != null)
			return parent.hasSpecial(name);
		return specials.containsKey(name);
	}

	public Namespace getNamespace(final String name, final Collection<String> ports) {
		return getNamespace(name, ports.stream().collect(Collectors.toMap(Function.identity(), Function.identity())));
	}

	public Namespace getNamespace(final String name, final Map<String, String> ports) {
		if (!namespaces.containsKey(name))
			namespaces.put(name, new Namespace(this, name, ports));
		return namespaces.get(name);
	}

	public void resolve() {
		resolve("");
	}

	private Set<String> resolve(final String prefix) {
		// resolve global signal names, making sure that each has a unique name. these
		// are either a dash-free plain name, eg. div[18], or a qualified version like
		// div[18]-aq if there are version that differ in case or qualified names are
		// forced.
		final Set<String> xnf = resolve(globals.values(), prefix);

		// sub-namespaces also have to be qualified so they are unique among themselves.
		// but they also have to be unique wrt the signal names: if there is a signal
		// foo/bar, then a namespace cannot be named foo because its sub-signal bar
		// would also be called foo/bar. instead, we simply rename it foo-1, giving
		// foo-1/bar which doesn't conflict. this simply increments until it's unique.
		// in the interest of prdictability of names, if there is a signal name prefixed
		// foo, then no namespace will be named foo, even if its sub-names wouldn't
		// actually conflict.
		final Set<String> prefixes = new HashSet<>();
		for (final String name : xnf) {
			final int slash = name.indexOf('/');
			prefixes.add(slash >= 0 ? name.substring(0, slash) : name);
		}
		// compute namespace names without prefix. that's easier to compare to the
		// prefixes, and the actual XNF name will be replaced anyway
		resolve(namespaces.values(), "");
		for (final Namespace ns : namespaces.values()) {
			String nsname = ns.getXnf();
			for (int i = 1; prefixes.contains(nsname); i++)
				nsname = ns.getXnf() + "-" + i;
			ns.setXnf(prefix + nsname);
			xnf.add(nsname);

			// sub-names in the namespace are prefixed with the namespace name. because the
			// namespace name doesn't match any prefix of signal names, this cannot produce
			// duplicates
			xnf.addAll(ns.resolve(prefix + nsname + "/"));
		}

		// anonymous names instead increment their counter until they're unique.
		// anonymous names are internal and don't have to be predictable anyway, so
		// having them depend on order of insertion is acceptable. they use no suffix
		// for the first instance, or a simple plain number for all further ones, giving
		// a sequence of foo, foo1, foo2...
		for (final Numbered name : derived) {
			String qname = name.getQualified(0);
			for (int i = 0; xnf.contains(qname); i++)
				qname = name.getQualified(i);
			name.setXnf(qname);
			xnf.add(qname);
		}
		return xnf;
	}

	private Set<String> resolve(final Iterable<? extends GlobalName> values, final String prefix) {
		// unqualified (original) names are kept unmodified if they're case-
		// insensitively unique. they're directly from the user-specified Verilog
		// design, so they have to be easily identifiable
		final Map<String, List<GlobalName>> collisions = new LinkedHashMap<>();
		for (final GlobalName name : values) {
			final String canon = name.getMangled().toLowerCase(Locale.ROOT);
			if (!collisions.containsKey(canon))
				collisions.put(canon, new ArrayList<>());
			collisions.get(canon).add(name);
		}

		// if not, they are mangled by appending their upper/lowercase bits in a Base32-
		// encoded qualifier. mangling can be enabled for all names to make them 100%
		// predictable, at the cost of always being different from the Verilog input
		final Set<String> xnf = new HashSet<>();
		for (final String target : collisions.keySet()) {
			final List<GlobalName> names = collisions.get(target);
			final boolean qualify = qualifyAllNames || names.size() > 1;
			for (final GlobalName name : names) {
				final String qname = qualify ? name.getQualified() : name.getMangled();
				name.setXnf(prefix + qname);
				xnf.add(qname);
			}
		}
		return xnf;
	}
}
