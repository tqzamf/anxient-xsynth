package xsynth.chips;

import java.util.List;

import xsynth.convert.PadFactory.Resistors;
import xsynth.convert.PadFactory.SlewRateControl;
import xsynth.convert.SpecialGateFactory;

public class XC3000Family extends ChipFamily {
	public XC3000Family() {
		super("XC3000/XC3100/XC300A/XC3100A", "3[01][0-9]{2}A?", 5, false, false, SlewRateControl.COARSE,
				Resistors.PULLUP_ONLY, false, false);
		bufferTypes.add("ACLK");
		bufferTypes.add("GCLK");
		customGates.put("OSC", new SpecialGateFactory(List.of("O"), List.of(), List.of("O")));
	}
}
