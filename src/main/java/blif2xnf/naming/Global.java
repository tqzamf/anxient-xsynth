package blif2xnf.naming;

class Global implements Name {
	private static final String BASE32 = "0123456789ABCDEFGHJKMNPRSTUVWXYZ";
	private static final String SUBSTITUTE = "-_[<]>";
	private static final String KEEP = "/$";
	private static final String ILLEGAL;
	static {
		final StringBuilder illegal = new StringBuilder();
		for (char ch = '!'; ch <= '~'; ch++)
			if (!Character.isAlphabetic(ch) && !Character.isDigit(ch) && SUBSTITUTE.indexOf(ch) < 0
					&& KEEP.indexOf(ch) < 0)
				illegal.append(ch);
		ILLEGAL = illegal.toString();
		assert illegal.length() == 24;
	}

	private final String mangled;
	private final String qualified;
	private String xnf;

	Global(final String name) {
		boolean digitsOnly = true;
		int bits = 0, accum = 0;
		final StringBuilder mangled = new StringBuilder();
		final StringBuilder qualifier = new StringBuilder();
		for (int i = 0; i < name.length(); i++) {
			final char ch = name.charAt(i);
			if (Character.isAlphabetic(ch)) {
				// characters are case-sensitive in BLIF/Verilog, but not in XNF. record each
				// character's case in the qualifier part, to make upper and lowercase names
				// different
				mangled.append(ch);
				accum = accum << 1 | (Character.isUpperCase(ch) ? 1 : 0);
				bits++;
				digitsOnly = false;
			} else if (SUBSTITUTE.indexOf(ch) >= 0) {
				// BLIF/Verilog uses [] for busses; XNF uses <>.
				// also replace - by _: this isn't necessary, but - is extremely rare (it needs
				// to be escaped in Verilog). it is more useful as a reserved delimiter for the
				// qualifier.
				// a bit in the qualifier records whether the character was replaced or not.
				final int index = SUBSTITUTE.indexOf(ch);
				mangled.append(SUBSTITUTE.charAt(index | 1));
				accum = accum << 1 | index & 1;
				bits++;
				digitsOnly = false;
			} else if (ch == '/') {
				// hierarchy separator. arguably, each hierarchy level could be mangled
				// separately. then again, tgt-blif probably only uses / for networks that
				// implement the LPM_* macros.
				mangled.append(ch);
				digitsOnly = false;
			} else if (ch == '$') {
				// an actual dollar sign. rare, but legal in unescaped verilog. because there
				// are <24 illegal characters to encode, $ itself can be represented with just 2
				// bits in the qualifier.
				mangled.append(ch);
				accum = accum << 2 | 3;
				bits += 2;
				digitsOnly = false;
			} else if (!Character.isDigit(ch)) {
				// XNF-illegal character. replaced by dollar sign and then simply encoded in the
				// qualifier.
				final int index = ILLEGAL.indexOf(ch);
				if (index < 0)
					throw new IllegalArgumentException("illegal character in name: »" + ch + "«");
				mangled.append('$');
				accum = accum << 5 | index;
				bits += 5;
				digitsOnly = false;
			} else
				// digit. valid as long as the identifier isn't entirely numeric
				mangled.append(ch);

			// encode 5 qualifier bits as 1 Base32 character if there are enough
			if (bits >= 5) {
				qualifier.append(BASE32.charAt(accum >> bits - 5 & 31));
				bits -= 5;
			}
		}
		// encode the remaining qualifier bits as a trailing Base32 character. these are
		// at most 4 bits because they would otherwise have been encoded within the loop
		if (bits > 0)
			qualifier.append(BASE32.charAt(accum << 5 - bits & 31));

		// identifiers cannot be all digits. (for no reason: they cannot be confused
		// with numbers in an XNF file because they only ever appear in string fields.)
		// so we always append the separator dash for an all-numeric identifier, giving
		// something that looks marginally less like a number...
		if (digitsOnly)
			mangled.append('-');
		this.mangled = mangled.toString();
		if (!digitsOnly)
			mangled.append('-');
		mangled.append(qualifier);
		qualified = mangled.toString();
	}

	public String getMangled() {
		return mangled;
	}

	public String getQualified() {
		return qualified;
	}

	@Override
	public String getXnf() {
		return xnf;
	}

	String setXnf(final String xnf) {
		return this.xnf = xnf;
	}
}
