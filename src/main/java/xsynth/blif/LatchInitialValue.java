package xsynth.blif;

public enum LatchInitialValue {
	/** 0: predictably 0 */
	RESET,
	/** 1: predictably 1 */
	PRESET,
	/** 2: unpredictable */
	DONTCARE,
	/** 3: unspecified */
	UNKNOWN
}