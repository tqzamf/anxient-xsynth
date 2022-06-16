package xsynth.chips;

import java.util.List;
import java.util.Map;

import xsynth.convert.PadFactory.Resistors;
import xsynth.convert.PadFactory.SlewRateControl;
import xsynth.convert.SpecialGateFactory;

public class XC4000Family extends ChipFamily {
	public XC4000Family() {
		super("XC4000", "4[0-9]{3}", 5, false, true, SlewRateControl.FINE, Resistors.PULLUP_PULLDOWN, true, true);
		bufferTypes.add("BUFGS");
		bufferTypes.add("BUFGP");
		customGates.put("BSCAN", new SpecialGateFactory( //
				List.of("TDO", "DRCK", "IDLE", "SEL1", "SEL2"), //
				List.of("TDI", "TMS", "TCK", "TDO1", "TDO2"), List.of(), List.of(), //
				Map.of("TDI", "TDI", "TDO", "TDO", "TMS", "TMS", "TCK", "TCK"), true));
		customGates.put("RDBK", new ReadbackFactory());
		customGates.put("STARTUP", new SpecialGateFactory(List.of("Q2", "Q3", "Q1Q4", "DONEIN"), //
				List.of("GSR", "GTS", "CLK"), List.of()));
		customGates.put("OSC4", new SpecialGateFactory(List.of("F8M", "F500K", "F16K", "F490", "F15"), //
				List.of(), List.of()));
		customGates.put("RAM", new RAMFactory(List.of("O"), List.of("A"), false, 5));
		// onlx XC4000E (and derivatives) have synchronous and dual-port RAM, but it's
		// easier not to make this two separate chips. XACTstep will always complain
		// about RAMS and RAMD because it doesn't support XC4000E; Alliance or ISE
		// should complain if targeting a non-E XC4000.
		customGates.put("RAMS", new RAMFactory(List.of("O"), List.of("A"), true, 5));
		customGates.put("RAMD", new RAMFactory(List.of("SPO", "DPO"), List.of("A", "DPRA"), true, 4));
	}
}
