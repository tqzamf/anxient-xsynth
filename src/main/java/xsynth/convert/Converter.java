package xsynth.convert;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.blif.BlifGate;
import xsynth.blif.BlifModel;
import xsynth.blif.BlifParser;
import xsynth.blif.Latch;
import xsynth.blif.LatchInitialValue;
import xsynth.blif.SumOfProducts;
import xsynth.blif.SumOfProducts.Product;
import xsynth.blif.SumOfProducts.ProductTerm;
import xsynth.chips.ChipFamily;
import xsynth.naming.Name;
import xsynth.naming.Namespace;
import xsynth.naming.SpecialName;
import xsynth.xnf.LatchType;
import xsynth.xnf.XnfNetlist;
import xsynth.xnf.XnfNetlist.Term;
import xsynth.xnf.XnfWriter;

public class Converter {
	private final Map<String, BlifModel> drivers = new LinkedHashMap<>();
	private final Map<String, BlifModel> consumers = new LinkedHashMap<>();
	private final Diagnostics diag;
	private final BlifParser reader;
	private final Namespace root;
	private final XnfNetlist xnf;
	private final boolean mergeToplevelNamespaces;

	public Converter(final Diagnostics diag, final ChipFamily family, final boolean qualifyAllNames,
			final boolean mergeToplevelNamespaces) {
		this.diag = diag;
		this.mergeToplevelNamespaces = mergeToplevelNamespaces;
		reader = new BlifParser(diag, family.getCustomGates(), family.getBufferTypes());
		root = new Namespace(qualifyAllNames);
		xnf = new XnfNetlist(family.getMaxGateInputs(), family.hasLatches(), family.hasLatchInitValue());
	}

	public void read(final String filename) throws IOException, AbortedException {
		try (final InputStream in = new FileInputStream(filename)) {
			read(in, filename);
		}
	}

	public void read(final InputStream in, final String filename) throws IOException, AbortedException {
		final BlifModel model = reader.parse(in, filename);
		final Set<String> outputs;
		final Set<String> inputs;
		if (mergeToplevelNamespaces) {
			// if the toplevel namespaces are merged, simply assume that *all* signals in
			// the model are ports
			outputs = model.getDrivers();
			inputs = model.getConsumers();
		} else {
			outputs = model.getOutputs();
			inputs = model.getInputs();
		}
		AbortedException err = null;
		for (final String out : outputs)
			if (drivers.containsKey(out)) {
				final BlifModel driver = drivers.get(out);
				err = diag.error(model.getSourceLocation(), "global signal " + out + " has multiple drivers");
				diag.info(driver.getSourceLocation(), "model " + driver.getName() + " also drives " + out);
			} else
				drivers.put(out, model);
		if (err != null)
			throw err;
		for (final String i : inputs)
			consumers.put(i, model);

		final Set<String> ports = new LinkedHashSet<>();
		ports.addAll(inputs);
		ports.addAll(outputs);
		final Namespace ns = root.getNamespace(model.getName(), ports);
		for (final BlifGate gate : model.getGates())
			if (gate instanceof final SumOfProducts sop)
				implementSumOfProducts(ns, model, sop);
			else if (gate instanceof final Latch latch)
				implementLatch(ns, model, latch);
			else if (gate instanceof final XnfCustomGate cg)
				cg.implement(xnf, ns, (signal, forceBuffer) -> getBufferedOutput(ns, model, signal, forceBuffer));
			else
				throw new UnsupportedOperationException("cannot implement unsupported "
						+ gate.getClass().getSimpleName() + " gate for outputs=" + gate.getOutputs());
	}

	public void writeTo(final OutputStream out, final String part, final List<String> cmdline) throws IOException {
		final Set<String> undriven = new HashSet<>(consumers.keySet());
		undriven.removeAll(drivers.keySet());
		for (final String sig : undriven) {
			diag.warn(consumers.get(sig).getSourceLocation(), "undriven global signal " + sig + ", assuming zero");
			xnf.addBuffer("BUF", root.getGlobal(sig), root.getSpecial(SpecialName.GND));
		}

		final Map<BlifModel, List<String>> unused = new LinkedHashMap<>();
		for (final String signal : drivers.keySet()) {
			final BlifModel model = drivers.get(signal);
			if (!unused.containsKey(model))
				unused.put(model, new ArrayList<>());
			unused.get(model).add(signal);
		}
		for (final BlifModel model : unused.keySet()) {
			final List<String> list = unused.get(model);
			list.removeAll(consumers.keySet());
			if (!list.isEmpty())
				diag.info(model.getSourceLocation(), "unused global signals: " + String.join(" ", list));
		}

		root.resolve();
		try (XnfWriter writer = new XnfWriter(out)) {
			writer.writeHeader(root, part, cmdline);
			writer.writeNetlist(xnf);
		}
	}

	private void implementSumOfProducts(final Namespace ns, final BlifModel model, final SumOfProducts sop) {
		final Name output = getBufferedOutput(ns, model, sop.getOutput(), null);
		if (sop.getTerms().size() == 0) {
			// no inputs = constant zero. connect to GND via a buffer so we don't have to
			// bother renaming nets
			xnf.addBuffer("BUF", output, ns.getSpecial(SpecialName.GND));
			return;
		}

		if (sop.getTerms().size() == 1) {
			// only a single product term, so the OR part of the AND-OR gate is omitted and
			// the AND gate drives the output directly.
			buildAndGate(ns, null, output, sop.getTerms().get(0));
			return;
		}

		// generic AND-OR gate, with the OR gate driving the output net
		final List<Term> sum = new ArrayList<>();
		for (final Product product : sop.getTerms())
			buildAndGate(ns, sum, output, product);
		xnf.addLogicGate("OR", output, false, sum);
	}

	private void buildAndGate(final Namespace ns, final List<Term> sum, final Name output, final Product product) {
		final List<Term> inputs = new ArrayList<>();
		for (final ProductTerm term : product.getTerms())
			inputs.add(new Term(ns.getGlobal(term.getInput()), term.isInvertInput()));
		if (inputs.isEmpty())
			// stupid boundary case where an AND gate has no inputs, and the output is
			// expected to always be high. substitute a non-inverted VCC input to make
			// the rest of the logic a lot easier.
			inputs.add(new Term(ns.getSpecial(SpecialName.VCC), false));

		if (inputs.size() == 1) {
			// AND term containing a single input (or nothing, aka VCC). we obviously cannot
			// create an actual AND gate for that.
			// connect directly to the OR gate, with appropriate inversion.
			final Term term = inputs.get(0);
			final boolean inverting = product.isInvertOutput() ^ term.invert();
			if (sum != null)
				// if we have an OR gate, connect the input to that instead, making sure the
				// inversion is correct
				sum.add(new Term(term.name(), inverting));
			else
				// if we're directly driving the output, create a buffer to drive it. we cannot
				// remove this buffer without removing or replacing the output net
				xnf.addBuffer(inverting ? "INV" : "BUF", output, term.name());
			return;
		}

		// general case: we have at least 2 product terms that we can collect using an
		// AND gate.
		final Name prod;
		if (sum != null) {
			// if we also have an OR gate, that AND outputs onto an anonymous net which
			// becomes an input to the OR gate
			prod = output.getAnonymous("PROD");
			sum.add(new Term(prod, false));
		} else
			// if we're expected to directly drive the output net, the AND gate connect to
			// that instead
			prod = output;
		xnf.addLogicGate("AND", prod, product.isInvertOutput(), inputs);
	}

	private void implementLatch(final Namespace ns, final BlifModel model, final Latch latch) {
		final Name output = getBufferedOutput(ns, model, latch.getDataOutput(), null);
		final Name input = ns.getGlobal(latch.getDataInput());
		final Name clock = latch.getClockInput() != null ? ns.getGlobal(latch.getClockInput())
				: ns.getSpecial(SpecialName.GCLK);

		final LatchType latchType = switch (latch.getType()) {
		case re, fe -> LatchType.FLIPFLOP;
		case ah, al -> LatchType.LATCH;
		default -> throw new UnsupportedOperationException("unsupported latch type " + latch.getType());
		};
		final boolean invertClock = switch (latch.getType()) {
		case re, ah -> false;
		case fe, al -> true;
		default -> throw new UnsupportedOperationException("unsupported latch type " + latch.getType());
		};
		xnf.addLatch(latchType, latch.getInitialValue() == LatchInitialValue.PRESET, output, input, clock, invertClock);
	}

	private Name getBufferedOutput(final Namespace ns, final BlifModel model, final String name,
			final String forceBuffer) {
		String buffer = model.getBuffer(name);
		if (forceBuffer != null) {
			if (buffer != null && !buffer.equalsIgnoreCase(forceBuffer))
				throw new IllegalArgumentException("signal " + name + " implicit .buffer " + forceBuffer
						+ " conflicts with specified .buffer " + buffer);
			buffer = forceBuffer;
		}

		final Name globalOutput = ns.getGlobal(name);
		final Name gateOutput;
		if (buffer != null) {
			// if a buffer type is specified, we create a buffer of that type and let it
			// drive the global network. the gate gets to drive an anonymous network
			// connected tothe buffer input
			gateOutput = globalOutput.getAnonymous(buffer.toUpperCase(Locale.ROOT));
			xnf.addBuffer(buffer, globalOutput, gateOutput);
		} else
			// for unbuffered signals, the gate gets to drive it directly (obviously)
			gateOutput = globalOutput;
		return gateOutput;
	}
}
