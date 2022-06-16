package xsynth.chips;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import xsynth.Diagnostics;
import xsynth.Diagnostics.AbortedException;
import xsynth.SourceLocation;
import xsynth.blif.CustomGate;
import xsynth.convert.PadFactory.Resistors;
import xsynth.convert.PadFactory.SlewRateControl;
import xsynth.convert.SpecialGateFactory;

public class XC5200Family extends ChipFamily {
	public XC5200Family() {
		super("XC5200", "52[0-9]{2}", 5, true, false, SlewRateControl.COARSE, Resistors.PULLUP_PULLDOWN, true, false);
		customGates.put("BSCAN", new SpecialGateFactory( //
				List.of("RESET", "UPDATE", "SHIFT", "TDO", "DRCK", "IDLE", "SEL1", "SEL2"), //
				List.of("TDI", "TMS", "TCK", "TDO1", "TDO2"), List.of(), List.of(), //
				Map.of("TDI", "TDI", "TDO", "TDO", "TMS", "TMS", "TCK", "TCK"), true));
		customGates.put("RDBK", new ReadbackFactory());
		customGates.put("STARTUP", new SpecialGateFactory(List.of("Q2", "Q3", "Q1Q4", "DONEIN"), //
				List.of("GR", "GTS", "CLK"), List.of()));
		customGates.put("OSC52", new OSC52());
	}

	private static class OSC52 extends SpecialGateFactory {
		private static int[] DIV1 = { 4, 16, 64, 256 };
		private static int[] DIV2 = { 2, 8, 32, 128, 1024, 4096, 16384, 65536 };
		private static List<String> OUTPUTS = new ArrayList<>();
		static {
			for (final int factor : DIV1)
				OUTPUTS.add("DIV" + factor);
			for (final int factor : DIV2)
				OUTPUTS.add("DIV" + factor);
		}

		public OSC52() {
			super(OUTPUTS, List.of("C"), List.of());
		}

		@Override
		public List<CustomGate> newInstance(final Diagnostics diag, final SourceLocation sloc, final String name,
				final List<String> flags, final Map<String, String> virtualOutputs, final Map<String, String> inputs)
				throws AbortedException {
			// if the clock input is connected, use that as the "user" clock. if nothing is
			// connected, use the internal RC osciallator instead
			final Map<String, String> params = new LinkedHashMap<>();
			params.put("OSC", inputs.containsKey("C") ? "USER" : "INTERNAL");
			// outputs are virtual, with their name encoding the divider. allocate them to
			// either OSC1 or OSC2, whichever supports the desired divider.
			final Map<String, String> realOutputs = new LinkedHashMap<>();
			allocatePort(diag, sloc, realOutputs, params, "DIVIDE1_BY", virtualOutputs, "OSC1", DIV1);
			allocatePort(diag, sloc, realOutputs, params, "DIVIDE2_BY", virtualOutputs, "OSC2", DIV2);
			return List.of(new SpecialGate(name, realOutputs, inputs, List.of(), params));
		}

		private void allocatePort(final Diagnostics diag, final SourceLocation sloc,
				final Map<String, String> realOutputs, final Map<String, String> params, final String dividerParam,
				final Map<String, String> virtualOutputs, final String realPort, final int[] dividerTaps)
				throws AbortedException {
			String osc = null;
			for (final int factor : dividerTaps) {
				final String signal = virtualOutputs.get("DIV" + factor);
				if (signal != null) {
					if (osc != null)
						throw diag.error(sloc, "conflicting OSC52 divisors: " + osc + " and " + signal + " both use "
								+ realPort + " / " + dividerParam);
					osc = signal;
					params.put(dividerParam, String.valueOf(factor));
				}
			}
			if (osc != null)
				realOutputs.put(realPort, osc);
		}
	}
}
