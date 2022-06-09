package xsynth.xnf;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import xsynth.XSynth;
import xsynth.naming.Namespace;
import xsynth.naming.SpecialName;

public class XnfWriter implements AutoCloseable {
	private final OutputStream xnf;

	public XnfWriter(final OutputStream xnf) {
		this.xnf = xnf;
	}

	public void writeHeader(final Namespace ns, final String part, final List<String> cmdline) throws IOException {
		final StringBuilder comment = new StringBuilder("\"");
		comment.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
		for (final String cmd : cmdline)
			comment.append(' ').append(cmd);
		comment.append('"');
		writeRecord(RecordType.LCANET, Map.of(), "6");
		writeRecord(RecordType.PROG, Map.of(), "XSynth", XSynth.getVersion(), comment.toString());
		if (part != null)
			writeRecord(RecordType.PART, Map.of(), part);
		writePower(ns, "0", SpecialName.GND);
		writePower(ns, "1", SpecialName.VCC);
	}

	private void writePower(final Namespace ns, final String power, final String special) throws IOException {
		if (ns.hasSpecial(special))
			writeRecord(RecordType.PWR, Map.of(), power, ns.getSpecial(special).getXnf());
	}

	public void writeSymbol(final XnfGate gate) throws IOException {
		final Map<String, String> params = new LinkedHashMap<>(gate.getParams());
		params.put("LIBVER", "2.0.0");
		writeRecord(RecordType.SYM, params, gate.getName().getXnf(), gate.getType());
		for (final XnfPin pin : gate.getPins()) {
			final Map<String, String> params1 = pin.getParams();
			if (pin.isInvert())
				params1.put("INV", null);
			writeRecord(RecordType.PIN, params1, pin.getPin(), pin.getDir().getCode(), pin.getSignal().getXnf(), "");
		}
		writeRecord(RecordType.END, Map.of());
	}

	public void writePad(final XnfPad pad) throws IOException {
		final Map<String, String> params = new LinkedHashMap<>(pad.getParams());
		for (final String flag : pad.getFlags())
			params.put(flag, null);
		params.put("LOC", pad.getLoc());
		writeRecord(RecordType.EXT, params, pad.getSignal().getXnf(), pad.getType().getCode(), "");
	}

	public void writeNetlist(final XnfNetlist netlist) throws IOException {
		for (final XnfGate gate : netlist.getGates())
			writeSymbol(gate);
		for (final XnfPad pad : netlist.getPads())
			writePad(pad);
	}

	private void writeRecord(final RecordType record, final Map<String, String> params, final String... fields)
			throws IOException {
		final StringBuilder buffer = new StringBuilder(record.name());
		for (final String field : fields)
			buffer.append(',').append(field);
		for (final String name : params.keySet()) {
			buffer.append(',').append(name);
			if (params.get(name) != null)
				buffer.append('=').append(params.get(name));
		}
		buffer.append("\r\n");
		writeLine(buffer.toString());
	}

	private void writeLine(final String line) throws IOException {
		xnf.write(line.getBytes(StandardCharsets.US_ASCII));
	}

	@Override
	public void close() throws IOException {
		writeRecord(RecordType.EOF, Map.of());
		xnf.close();
	}
}
