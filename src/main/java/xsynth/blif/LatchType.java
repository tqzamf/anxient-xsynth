package xsynth.blif;

public enum LatchType {
	/** flip flop, rising edge */
	re,
	/** flip flop, falling edge */
	fe,
	/** latch, active high */
	ah,
	/** latch, active low */
	al,
	/** asynchronous (wtf?) */
	as
}