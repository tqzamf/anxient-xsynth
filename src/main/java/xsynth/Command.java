package xsynth;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import xsynth.Diagnostics.AbortedException;
import xsynth.Option.ConsumedArgument;

public abstract class Command {
	private static final int MAXLINELENGTH = 76;

	@Argument(shortOption = 'q', longOptions = "quiet", help = "suppress INFO messages")
	public boolean quiet;

	protected final String name;
	protected final List<String> cmdline;
	private final List<Option> options = new ArrayList<>();
	private final List<String> infiles = new ArrayList<>();
	protected final Diagnostics diag;

	public Command(final Diagnostics diag, final List<String> cmdline) throws AbortedException {
		this.diag = diag;
		name = cmdline.get(0);
		this.cmdline = cmdline;
		for (final Field field : getClass().getFields()) {
			final Argument opt = field.getAnnotation(Argument.class);
			if (opt != null)
				options.add(new Option(opt, field));
		}

		nextOption: for (int cmdPos = 1; cmdPos < cmdline.size(); cmdPos++) {
			final String arg = cmdline.get(cmdPos);
			if (arg.equals("--help"))
				throw usage(null);

			if (arg.equals("--")) {
				infiles.addAll(cmdline.subList(cmdPos + 1, cmdline.size()));
				break nextOption;

			} else if (arg.startsWith("--")) {
				final int pos = arg.indexOf('=');
				final String argName = arg.substring(2, pos < 0 ? arg.length() : pos);
				for (final Option opt : options)
					for (final String n : opt.arg.longOptions())
						if (n.startsWith(argName)) {
							if (opt.set(this, cmdline, cmdPos, pos + 1) == ConsumedArgument.SEPARATE)
								cmdPos++; // if value was in a separate argument, skip it
							continue nextOption;
						}
				throw usage("--" + argName + ": unknown option");

			} else if (arg.startsWith("-")) {
				int charPos = 1;
				nextChar: for (; charPos < arg.length(); charPos++) {
					for (final Option opt : options)
						if (opt.arg.shortOption() == arg.charAt(charPos)) {
							final ConsumedArgument consumedArgument = opt.set(this, cmdline, cmdPos, charPos + 1);
							if (consumedArgument == ConsumedArgument.NONE)
								// if single-character no-argument option, the rest of the arg is additional
								// option switches
								continue nextChar;
							// else it consumed *something*, so we're certainly done with the current arg
							if (consumedArgument == ConsumedArgument.SEPARATE)
								// and if we read the value from a separate argument, skip that one, too
								cmdPos++;
							continue nextOption;
						}
					throw usage("-" + arg.charAt(charPos) + ": unknown option");
				}

			} else
				infiles.add(arg);
		}

		for (final Option opt : options)
			if (opt.arg.required() && opt.hasArg() && opt.isNull(this))
				throw usage("--" + opt + ": missing required option");
	}

	public void execute() throws AbortedException {
		diag.setQuiet(quiet);
		execute(infiles);
	}

	public abstract void execute(final List<String> infiles) throws AbortedException;

	protected AbortedException usage(final String msg) {
		diag.setQuiet(false);
		final AbortedException err = msg != null ? diag.error(null, msg)
				: new AbortedException(null, "invalid commandline syntax");

		diag.info(null, "usage: xsynth " + getName(getClass()) + " [options...] [input files...]");
		int maxoptlen = 0;
		for (final Option opt : options) {
			final int optlen = opt.getDescription().length();
			if (optlen > maxoptlen)
				maxoptlen = optlen;
		}

		final StringBuilder line = new StringBuilder();
		for (final Option opt : options) {
			line.setLength(0);
			line.append("  ");
			line.append(opt.getDescription());
			final String[] words = opt.arg.help().split(" ");
			int i = 0;
			while (i < words.length) {
				while (line.length() < maxoptlen + 3)
					line.append(' ');
				while (i < words.length && line.length() + 1 + words[i].length() < MAXLINELENGTH)
					line.append(' ').append(words[i++]);
				diag.info(null, line.toString());
				line.setLength(0);
			}
		}
		return err;
	}

	public static String getName(final Class<? extends Command> clazz) {
		return clazz.getAnnotation(CommandName.class).value();
	}

	public static <T extends Command> T newInstance(final Class<T> clazz, final Diagnostics diag,
			final List<String> cmdline) throws AbortedException {
		try {
			return clazz.getConstructor(Diagnostics.class, List.class).newInstance(diag, cmdline);
		} catch (final InvocationTargetException e) {
			if (e.getTargetException() instanceof AbortedException)
				throw (AbortedException) e.getTargetException();
			else
				throw new RuntimeException(e);
		} catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}
}
