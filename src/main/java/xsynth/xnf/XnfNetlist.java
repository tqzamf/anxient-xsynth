package xsynth.xnf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import xsynth.naming.Name;

public class XnfNetlist {
	private final List<XnfGate> gates = new ArrayList<>();
	private final List<XnfPad> pads = new ArrayList<>();
	private final int maxGateInputs;
	private final boolean hasLatches;
	private final boolean hasLatchInitValue;

	public XnfNetlist(final int maxGateInputs, final boolean hasLatches, final boolean hasLatchInitValue) {
		this.maxGateInputs = maxGateInputs;
		this.hasLatches = hasLatches;
		this.hasLatchInitValue = hasLatchInitValue;
	}

	public XnfGate addSymbol(final String type, final Map<String, String> params) {
		final XnfGate gate = new XnfGate(type, params);
		gates.add(gate);
		return gate;
	}

	public void addPad(final PadType type, final Name signal, final String loc, final Map<String, String> params,
			final List<String> flags) throws IOException {
		pads.add(new XnfPad(type, signal, loc, params, flags));
	}

	public void addLogicGate(final String type, final Name output, final List<Term> inputs) {
		if (inputs.size() <= 1)
			throw new IllegalArgumentException("too few inputs: " + inputs);

		// algorithm: create a gate and try to cram all inputs into that gate. if they
		// don't fit, wire its output to a temporary net and append that net to the end
		// of the queue.
		// this keeps the invariant that either the gate has at least 2 inputs
		// connected, or there are still signals in the queue. thus when the loop ends
		// (and signals is empty), the gate has at least 2 inputs connected.
		final Queue<Term> signals = new LinkedList<>(inputs);
		XnfGate gate = addSymbol(type, null);
		int n = 0;
		while (!signals.isEmpty()) {
			if (n >= maxGateInputs) {
				// in order to keep the invariant, we only create the intermediate net once we
				// actually know that there will be at least one more signal to combine it with.
				final Name intermediate = output.getAnonymous(type);
				gate.connect(PinDirection.DRIVER, "O", false, intermediate, null);
				signals.add(new Term(intermediate, false));

				gate = addSymbol(type, null);
				n = 0;
			}

			final Term sig = signals.remove();
			gate.connect(PinDirection.CONSUMER, "I" + n, sig.invert, sig.name, null);
			n++;
		}
		gate.connect(PinDirection.DRIVER, "O", false, output, null);
	}

	public void addBuffer(final String type, final Name output, final Name input) {
		System.out.println(type + " " + output + " " + input);
		final XnfGate gate = addSymbol(type, null);
		gate.connect(PinDirection.CONSUMER, "I", false, input, null);
		gate.connect(PinDirection.DRIVER, "O", false, output, null);
	}

	public void addLatch(final LatchType type, final boolean initSet, final Name output, final Name input,
			final Name clock, final boolean invertClock) {
		if (type == LatchType.LATCH && !hasLatches)
			throw new IllegalArgumentException("latches not supported by chip: " + output);

		final Map<String, String> params;
		final Name d, q;
		if (hasLatchInitValue) {
			// the simple case: chip simply supports INIT=S or INIT=R to set initial value
			params = Map.of("INIT", initSet ? "S" : "R");
			d = input;
			q = output;
		} else if (initSet) {
			// the complex case: we need an initially-set latch, but the chip doesn't let us
			// specify that. so we use an initially-reset latch instead and add inverters to
			// its input and output pins
			params = null;
			d = input.getAnonymous("INV");
			addBuffer("INV", d, input);
			q = output.getAnonymous("INV");
			addBuffer("INV", output, q);
		} else {
			// the convenient case: we only have initially-reset latches, but that's what we
			// need
			params = null;
			d = input;
			q = output;
		}
		final XnfGate gate = addSymbol(type.getSymbol(), params);
		gate.connect(PinDirection.CONSUMER, type.getClockPin(), invertClock, clock, null);
		gate.connect(PinDirection.CONSUMER, "D", false, d, null);
		gate.connect(PinDirection.DRIVER, "Q", false, q, null);
	}

	public List<XnfGate> getGates() {
		return gates;
	}

	public List<XnfPad> getPads() {
		return pads;
	}

	public static record Term(Name name, boolean invert) {
	}
}
