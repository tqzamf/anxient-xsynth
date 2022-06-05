// iverilog -t blif -o blinker.blif blinker.v
module blinker(
	input clock,
	input reset,
	input [1:0] sel,
	output [0:2] led_static,
	output reg[0:2] led);

reg [33:0] div;
reg [0:2] shift;

assign led_static = shift;

always @(posedge clock) begin
	if (reset)
		div <= 0;
	else
		div <= div + 1;
end

always @(posedge div[24]) begin
	if (reset)
		shift <= 3'b001;
	else
		shift[0:2] <= {!shift[2], shift[0:1]};
end

always @(posedge clock) begin
	case(sel)
	2'b00: led <= shift;
	2'b01: led <= ~shift;
	2'b10: led <= 3'b000;
	2'b11: led <= 3'b111;
	endcase
end

endmodule
