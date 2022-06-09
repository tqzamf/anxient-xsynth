package xsynth;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

import xsynth.Diagnostics.AbortedException;
import xsynth.convert.ConvertCommand;

public class XSynth {
	@SuppressWarnings("unchecked")
	private static Class<? extends Command>[] COMMANDS = new Class[] { ConvertCommand.class };
	private static String version;

	public static void main(final String... args) {
		final Diagnostics diag = new Diagnostics();
		if (args.length == 0) {
			usage(diag);
			System.exit(1);
		}

		try {
			final List<String> cmdline = List.of(args);
			final Class<? extends Command> cmd = findCommand(diag, args[0]);
			Command.newInstance(cmd, diag, cmdline).execute();
		} catch (final AbortedException e) {
			System.exit(1);
		}
	}

	private static Class<? extends Command> findCommand(final Diagnostics diag, final String name) {
		for (final Class<? extends Command> cmd : COMMANDS)
			if (Command.getName(cmd).equals(name))
				return cmd;
		if (name.equals("--version") || name.equals("version")) {
			diag.info(null, "XSynth version " + getVersion());
			System.exit(0);
		}
		if (!name.equals("--help") && !name.equals("help"))
			diag.error(null, "invalid commandline syntax");
		usage(diag);
		System.exit(1);
		return null;
	}

	private static void usage(final Diagnostics diag) {
		final List<String> help = List.of("help", "--help");
		for (final Class<? extends Command> cmd : COMMANDS)
			try {
				Command.newInstance(cmd, diag, help);
			} catch (final AbortedException e) {
				// ignore
			}
	}

	public static String getVersion() {
		if (version == null) {
			final Properties props = new Properties();
			try {
				props.load(XSynth.class.getResourceAsStream("version.properties"));
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
			version = props.getProperty("version") + "-" + props.getProperty("branch") + "-"
					+ props.getProperty("commit").substring(0, 7);
		}
		return version;
	}
}
