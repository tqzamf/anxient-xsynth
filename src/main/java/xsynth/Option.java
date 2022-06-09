package xsynth;

import java.lang.reflect.Field;
import java.util.List;

import xsynth.Diagnostics.AbortedException;

class Option {
	final Argument arg;
	private final Field field;

	public Option(final Argument arg, final Field field) {
		this.arg = arg;
		this.field = field;
	}

	boolean hasArg() {
		return !field.getType().equals(Boolean.TYPE);
	}

	String getDescription() {
		final StringBuilder buffer = new StringBuilder();
		if (arg.shortOption() > 0)
			buffer.append('-').append(arg.shortOption());
		for (final String longopt : arg.longOptions()) {
			if (buffer.length() > 0)
				buffer.append(',');
			buffer.append("--").append(longopt);
		}
		if (hasArg())
			buffer.append('=').append(arg.metavar());
		return buffer.toString();
	}

	Option.ConsumedArgument set(final Command command, final List<String> cmdline, final int cmdPos,
			final int valueStart) throws AbortedException {
		final String arg = cmdline.get(cmdPos);
		try {
			final boolean hasInlineArg = valueStart > 0 && valueStart < arg.length();
			if (!hasArg()) {
				if (hasInlineArg && arg.startsWith("--"))
					throw command.usage("--" + this + ": does not take an argument");
				field.set(command, true);
				return ConsumedArgument.NONE;
			}

			String value;
			if (!hasInlineArg) {
				if (cmdPos >= cmdline.size())
					throw command.usage("--" + this + ": missing argument");
				value = cmdline.get(cmdPos + 1);
			} else
				value = arg.substring(valueStart);
			field.set(command, value);
			return hasInlineArg ? ConsumedArgument.INLINE : ConsumedArgument.SEPARATE;
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	boolean isNull(final Command command) {
		try {
			return field.get(command) == null;
		} catch (IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String toString() {
		return arg.longOptions()[0];
	}

	enum ConsumedArgument {
		NONE, INLINE, SEPARATE
	}
}