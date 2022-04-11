package blif2xnf.naming;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Names {
	private final Map<String, Global> globals = new HashMap<>();
	private final List<Anonymous> derived = new ArrayList<>();
	private final boolean qualifyAllNames;

	public Names(final boolean qualifyAllNames) {
		this.qualifyAllNames = qualifyAllNames;
	}

	public Global getGlobal(final String name) {
		if (!globals.containsKey(name))
			globals.put(name, new Global(name));
		return globals.get(name);
	}

	public Anonymous getAnonymous(final Global base, final String qualifier) {
		final Anonymous name = new Anonymous(base, qualifier);
		derived.add(name);
		return name;
	}

	public void resolve() {
		// unqualified (original) names are kept unmodified if they're case-
		// insensitively unique. they're directly from the user-specified Verilog
		// design, so they have to be easily identifiable
		final Map<String, List<Global>> collisions = new HashMap<>();
		for (final Global name : globals.values()) {
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
			final List<Global> names = collisions.get(target);
			final boolean qualify = qualifyAllNames || names.size() > 1;
			for (final Global name : names)
				xnf.add(name.setXnf(qualify ? name.getQualified() : name.getMangled()));
		}

		// anonymous names instead increment their counter until they're unique.
		// anonymous names are internal and don't have to be predicatable anyway, so
		// having them depend on order of insertion is acceptable
		for (final Anonymous name : derived) {
			int i = 0;
			while (xnf.contains(name.getQualified(i)))
				i++;
			xnf.add(name.setXnf(name.getQualified(i)));
		}
	}

}
