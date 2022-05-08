package xsynth.naming;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Names {
	private final Map<String, SpecialName> specials = new HashMap<>();
	private final Map<String, GlobalName> globals = new HashMap<>();
	private final List<Numbered> derived = new ArrayList<>();
	private final boolean qualifyAllNames;

	public Names(final boolean qualifyAllNames) {
		this.qualifyAllNames = qualifyAllNames;
	}

	public GlobalName getGlobal(final String name) {
		if (!globals.containsKey(name))
			globals.put(name, new GlobalName(name));
		return globals.get(name);
	}

	public AnonymousName getAnonymous(final GlobalName base, final String qualifier) {
		final AnonymousName name = new AnonymousName(base, qualifier);
		derived.add(name);
		return name;
	}

	public Name getSpecial(final String name) {
		if (!specials.containsKey(name)) {
			final SpecialName net = new SpecialName(name);
			specials.put(name, net);
			derived.add(net);
		}
		return specials.get(name);
	}

	public boolean hasSpecial(final String name) {
		return specials.containsKey(name);
	}

	public void resolve() {
		// unqualified (original) names are kept unmodified if they're case-
		// insensitively unique. they're directly from the user-specified Verilog
		// design, so they have to be easily identifiable
		final Map<String, List<GlobalName>> collisions = new HashMap<>();
		for (final GlobalName name : globals.values()) {
			final String canon = name.getMangled().toLowerCase();
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
			for (final GlobalName name : names)
				xnf.add(name.setXnf(qualify ? name.getQualified() : name.getMangled()));
		}

		// anonymous names instead increment their counter until they're unique.
		// anonymous names are internal and don't have to be predictable anyway, so
		// having them depend on order of insertion is acceptable
		for (final Numbered name : derived) {
			int i = 0;
			while (xnf.contains(name.getQualified(i)))
				i++;
			xnf.add(name.setXnf(name.getQualified(i)));
		}
	}
}
