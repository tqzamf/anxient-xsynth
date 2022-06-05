package xsynth.blif;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.SourceLocation;

public class BlifParser {
	public static final String IOPAD_GATE = "iopad";

	private final Map<String, CustomGateFactory> customGates;
	private final Diagnostics diag;
	private final Map<String, BlifModel> models = new LinkedHashMap<>();
	private transient int nModels;
	private transient BlifModel primaryModel;
	private transient BlifModel model;
	private transient SumOfProducts sop;

	public BlifParser(final Diagnostics diag, final Map<String, CustomGateFactory> customGates) {
		this.diag = diag;
		this.customGates = customGates;
	}

	public BlifModel parse(final String filename) throws IOException, AbortedException {
		return parse(new BlifReader(diag, filename));
	}

	public BlifModel parse(final InputStream in, final String filename) throws IOException, AbortedException {
		return parse(new BlifReader(diag, in, filename));
	}

	public BlifModel parse(final BlifReader reader) throws IOException, AbortedException {
		nModels = 0;
		try {
			AbortedException err = null;
			while (true) {
				final List<String> line = reader.nextLine();
				if (line == null)
					break;
				try {
					parseLine(reader.getCurrentLocation(), line);
				} catch (final AbortedException e) {
					err = e;
				}
			}
			if (primaryModel == null && err == null) // don't put that after syntax errors
				err = diag.error(reader.getCurrentLocation(), "file doesn't contain any models");
			for (final BlifModel model : models.values())
				model.inferIO(diag);
			if (err != null)
				throw err;
			return primaryModel;
		} finally {
			model = null;
			sop = null;
			primaryModel = null;
			reader.close();
		}
	}

	private void parseLine(final SourceLocation sloc, final List<String> line) throws AbortedException {
		final String decl = line.get(0).toLowerCase(Locale.ROOT);
		if (decl.equals(".model")) {
			if (line.size() != 2)
				throw diag.error(sloc, line, "illegal .model declaration");
			createModel(sloc, line.get(1));

		} else if (decl.equals(".end")) {
			if (line.size() != 1)
				throw diag.error(sloc, line, "illegal .end declaration");
			if (model == null)
				diag.warn(sloc, ".end declaration outside model");
			model = null;

		} else if (decl.startsWith(".")) {
			if (model == null)
				createModel(sloc, getImplicitModelName(sloc));

			switch (decl) {
			case ".inputs" -> model.addInputs(parseNameList(line, 1, 0));
			case ".outputs" -> model.addOutputs(parseNameList(line, 1, 0));
			case ".clock" -> model.addClocks(parseNameList(line, 1, 0));
			case ".buffer" -> { // proprietary
				if (line.size() < 2)
					throw diag.error(sloc, line, "illegal .buffer declaration");
				try {
					model.addBuffers(line.get(1), parseNameList(line, 2, 0));
				} catch (final IllegalArgumentException e) {
					throw diag.error(sloc, e.getMessage());
				}
			}
			default -> {
				try {
					model.addGate(parseGate(sloc, decl, line));
				} catch (final IllegalArgumentException e) {
					throw diag.error(sloc, e.getMessage());
				}
			}
			}

		} else {
			if (model == null)
				throw diag.error(sloc, "output cover outside a model");
			if (sop == null)
				throw diag.error(sloc, "output cover not part of .names");
			final String out = line.get(line.size() - 1);
			if (out.length() != 1)
				throw diag.error(sloc, line, "illegal output cover " + out);

			final StringBuilder in = new StringBuilder();
			for (int i = 0; i < line.size() - 1; i++)
				in.append(line.get(i));
			try {
				sop.addProductTerm(out.charAt(0), in.toString().toCharArray());
			} catch (final IllegalArgumentException e) {
				throw diag.error(sloc, line, e.getMessage());
			}
		}
	}

	private void createModel(final SourceLocation sloc, final String name) throws AbortedException {
		model = new BlifModel(name, sloc);
		if (models.containsKey(name)) {
			final AbortedException err = diag.error(sloc, "model " + name + " redeclared");
			diag.info(models.get(name).getSourceLocation(), "previous declaration was here");
			throw err;
		}
		if (primaryModel == null)
			primaryModel = model;
		models.put(name, model);
		nModels++;
	}

	private String getImplicitModelName(final SourceLocation sloc) {
		final String filename = sloc.getFilename().replaceFirst("(?i)\\.bli?f$", "").replaceFirst(".*[\\/]", "");
		if (nModels == 0) {
			// primary model is unnamed, specified to be implicitly named after the
			// filename. the primary model can actually be referenced reliably using this
			// name, and usually isn't referenced at all so in most cases its name is
			// irrelevant.
			diag.info(sloc, "implicitly named primary model " + filename);
			return filename;
		}

		// a secondary model is unnamed. there isn't really a specified name in this
		// case because "the" filename obviously need not be unique. that's fine if the
		// model isn't actually referenced, but it's a bad idea in any case.
		final String name = filename + "/model" + nModels;
		diag.warn(sloc, "implicitly named submodel " + name + " cannot be referenced in a portable way");
		return name;
	}

	private List<String> parseNameList(final List<String> line, final int first, final int last) {
		final List<String> names = new ArrayList<>(line.size() - first - last);
		for (int i = first; i < line.size() - last; i++)
			names.add(line.get(i));
		return names;
	}

	private BlifGate parseGate(final SourceLocation sloc, final String decl, final List<String> line)
			throws AbortedException {
		return switch (decl) {
		case ".names" -> {
			if (line.size() < 2)
				throw diag.error(sloc, line, "illegal .names declaration");
			sop = new SumOfProducts(line.get(line.size() - 1), parseNameList(line, 1, 1));
			yield sop;
		}

		case ".latch" -> {
			if (line.size() < 3)
				throw diag.error(sloc, line, "illegal .latch declaration");
			final String input = line.get(1);
			final String output = line.get(2);

			final String latchtype;
			final String clock;
			int pos = 3;
			if (line.size() > pos + 1 && Latch.LATCH_TYPES.contains(line.get(pos).toLowerCase(Locale.ROOT))) {
				latchtype = line.get(pos);
				clock = line.get(pos + 1);
				pos += 2;
			} else
				latchtype = clock = null;

			final String initval;
			if (line.size() > pos && Latch.INITIAL_VALUES.contains(line.get(pos))) {
				initval = line.get(pos);
				pos++;
			} else
				initval = null;

			if (line.size() > pos)
				throw diag.error(sloc, line, "trailing garbage in .latch declaration");
			try {
				yield new Latch(output, input, latchtype, clock, initval);
			} catch (final IllegalArgumentException e) {
				throw diag.error(sloc, line, e.getMessage());
			}
		}

		case ".gate" -> { // semi-proprietary: multiple outputs, flags
			if (line.size() < 2)
				throw diag.error(sloc, line, "illegal .gate declaration");
			final String type = line.get(1).toLowerCase(Locale.ROOT);
			if (!customGates.containsKey(type))
				throw diag.error(sloc, "unsupported .gate " + type);
			yield parseCustomGate(sloc, customGates.get(type), type, line);
		}
		case ".pad" -> { // proprietary
			if (line.size() < 2)
				throw diag.error(sloc, line, "illegal .pad declaration");
			final String pad = line.get(1).toUpperCase(Locale.ROOT);
			yield parseCustomGate(sloc, customGates.get(CustomGateFactory.IOPAD_GATE), pad, line);
		}

		default -> throw diag.error(sloc, line, "unsupported declaration");
		};
	}

	private CustomGate parseCustomGate(final SourceLocation sloc, final CustomGateFactory factory, final String name,
			final List<String> line) throws AbortedException {
		final Map<String, String> inputs = new LinkedHashMap<>();
		final Map<String, String> outputs = new LinkedHashMap<>();
		final List<String> flags = new ArrayList<>();

		AbortedException err = null;
		for (int i = 2; i < line.size(); i++) {
			final String decl = line.get(i);
			final int eq = decl.indexOf('=');
			if (eq < 0) {
				final String flag = decl.toLowerCase(Locale.ROOT);
				if (!factory.getFlags().contains(flag))
					err = diag.error(sloc, "unknown flag " + flag);
				if (flags.contains(flag))
					diag.info(sloc, "duplicate flag " + flag);
				flags.add(flag);
				continue;
			}

			final String pin = decl.substring(0, eq).toLowerCase(Locale.ROOT);
			final String signal = decl.substring(eq + 1);
			if (pin.isEmpty())
				err = diag.error(sloc, "missing pin name");
			if (signal.isEmpty())
				err = diag.error(sloc, "missing signal name for pin " + pin);
			final String existingConnection = inputs.containsKey(pin) ? inputs.get(pin) : outputs.get(pin);
			if (existingConnection != null)
				err = diag.error(sloc, "duplicate connection: gate pin " + pin + " connects to both " + signal + " and "
						+ existingConnection);
			if (factory.getInputs().contains(pin))
				inputs.put(pin, signal);
			else if (factory.getOutputs().contains(pin))
				outputs.put(pin, signal);
			else
				err = diag.error(sloc, "connection to nonexistent gate pin " + pin);
		}

		for (final String pin : factory.getRequiredSignals())
			if (!inputs.containsKey(pin) && !outputs.containsKey(pin))
				err = diag.error(sloc, "required gate pin " + pin + " not connected");
		if (err != null)
			throw err;
		return factory.newInstance(diag, sloc, name, flags, outputs, inputs);
	}

	public Map<String, BlifModel> getModels() {
		return models;
	}
}
