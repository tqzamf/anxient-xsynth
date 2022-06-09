package xsynth.convert;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.NoSuchElementException;

import xsynth.Argument;
import xsynth.Command;
import xsynth.CommandName;
import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.chips.ChipFamily;

@CommandName("convert")
public class ConvertCommand extends Command {
	@Argument(shortOption = 'm', longOptions = "merge", help = "merge the signal namespaces of all BLIF files"
			+ " specified on the command line, connecting the models' internal signals by name. otherwise,"
			+ " only the models' external inputs, outputs and clock inputs are connected by name.")
	public boolean mergeToplevelNamespaces;
	@Argument(longOptions = "qualify-names", help = "append signal qualifier to all names, even if the signal"
			+ " name is already unique. if enabled, 'foobar' in BLIF always becomes foobar-00 in XNF,"
			+ " regardless of whether a conflicting signal (eg. FOObar / FOObar-W0) exists in BLIF or not."
			+ " the downside, of course, is that XNF names never match BLIF / Verilog names.")
	public boolean qualifyAllNames;
	@Argument(shortOption = 'p', longOptions = "part", metavar = "PART", help = "specify part name to write to"
			+ " the XNF file. avoids having to specify it for every XACTstep command. if omitted,"
			+ " --target-family has to be given so the correct set of chip-specific gates is loaded."
			+ " note that part names are specified without the XC prefix, as for XACTstep.")
	public String part;
	@Argument(shortOption = 'f', longOptions = "family", metavar = "FAMILY", help = "specify the chip family"
			+ " so that the correct set of chip-specific gates is loaded. required if --part is omitted."
			+ " supported values: 2000 3000 5200")
	public String family;
	@Argument(shortOption = 'o', longOptions = "output", metavar = "FILE", required = true, help = "output XNF file")
	public String outfile;

	public ConvertCommand(final Diagnostics diag, final List<String> cmdline) throws AbortedException {
		super(diag, cmdline);
	}

	@Override
	public void execute(final List<String> infiles) throws AbortedException {
		if (family == null) {
			if (part == null)
				throw usage("either --family or --part is required");
			family = part;
		}
		if (infiles.isEmpty())
			throw usage("no input files");
		final ChipFamily chipFamily;
		try {
			chipFamily = ChipFamily.forPart(family);
		} catch (final NoSuchElementException e) {
			throw diag.error(null, e.getMessage());
		}

		final Converter converter = new Converter(diag, chipFamily, qualifyAllNames, mergeToplevelNamespaces);
		for (final String infile : infiles)
			try {
				converter.read(infile);
			} catch (final IOException e) {
				throw diag.error(null,
						"failed to read " + infile + ": " + e.getClass().getSimpleName() + " " + e.getMessage());
			}
		try (final OutputStream buffer = new FileOutputStream(outfile)) {
			converter.writeTo(buffer, part, cmdline);
		} catch (final IOException e) {
			throw diag.error(null,
					"failed to write " + outfile + ": " + e.getClass().getSimpleName() + " " + e.getMessage());
		}
	}
}
