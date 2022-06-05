package xsynth.chips;

public class XC3000Family extends ChipFamily {
	public XC3000Family() {
		super("XC3000/XC3100/XC300A/XC3100A", "3[01][0-9]{2}A?", 5, false, false, true, false, false, false);
	}
}