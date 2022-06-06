package xsynth.chips;

import xsynth.convert.PadFactory.Resistors;
import xsynth.convert.PadFactory.SlewRateControl;

public class XC2000Family extends ChipFamily {
	public XC2000Family() {
		super("XC2000", "20[0-9]{2}", 4, true, false, SlewRateControl.NONE, Resistors.NONE, false, false);
		bufferTypes.add("ACLK");
		bufferTypes.add("GCLK");
	}
}
