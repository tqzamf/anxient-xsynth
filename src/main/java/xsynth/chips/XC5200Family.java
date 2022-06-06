package xsynth.chips;

import xsynth.convert.PadFactory.Resistors;
import xsynth.convert.PadFactory.SlewRateControl;

public class XC5200Family extends ChipFamily {
	public XC5200Family() {
		super("XC5200", "52[0-9]{2}", 5, true, false, SlewRateControl.COARSE, Resistors.PULLUP_PULLDOWN, true, false);
	}
}
