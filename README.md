# purpose

program Xilinx first-generation (XC2000, XC3000, XC5200) FPGAs in Verilog, using Icarus Verilog 11.0 `tgt-blif` and
XACTstep 5.2.0. Icarus Verilog does the heavy lifting of translating Verilog into gates and flipflops in BLIF format,
XSynth (this program) turns that BLIF file into and XNF file, and XACTstep then maps, places and routes the design an
finally generates a bitstream for programming the device.

why? those chips are slowly becoming old enough that they are turning from obsolete junk into historically interesting
artifacts. after all, the XC2064 is the world's first (SRAM-programmable) FPGA, the chip that started it all off. the
period-accurate design methodology would have been schematic entry, but Verilog is a better way for modern designers to
get a feeling for how powerful (or not) those vintage chips were.

note that neither Verilog nor Icarus Verilog is completely anachronistic. there is plenty of evidence that people used
Icarus Verilog to target Xilinx chips around 2000, though probably not the very oldest ones. the relevant `tgt-fpga`
has however been disabled since Icarus Verilog 0.9, circa 2005, and while it's still in the repos, it doesn't readily
work (or compile, for that matter).

# installation

XSynth is an ordinary Java project, built into a JAR using Maven. you can then copy that JAR to a convenient place, and
possibly create a start script for it:

```
mvn clean package && cp target/xsynth-*.jar /opt/xilinx/xsynth.jar
```

do make sure that Maven (3.6-ish) and Java 17+ are installed, though. both are in Debian's repos as of Debian 11, as is
Icarus Verilog 11.0.

# usage

XSynth uses subcommands so that the single JAR can implement multiple functionalities. so far, the only subcommand is
`convert`, which converts BLIF files to XNF files:

```
usage: xsynth convert [options...] [input files...]
  -m,--merge          merge the signal namespaces of all BLIF files
                      specified on the command line, connecting the models'
                      internal signals by name. otherwise, only the models'
                      external inputs, outputs and clock inputs are
                      connected by name.
  --qualify-names     append signal qualifier to all names, even if the
                      signal name is already unique. if enabled, 'foobar'
                      in BLIF always becomes foobar-00 in XNF, regardless
                      of whether a conflicting signal (eg. FOObar /
                      FOObar-W0) exists in BLIF or not. the downside, of
                      course, is that XNF names never match BLIF / Verilog
                      names.
  -p,--part=PART      specify part name to write to the XNF file. avoids
                      having to specify it for every XACTstep command. if
                      omitted, --target-family has to be given so the
                      correct set of chip-specific gates is loaded. note
                      that part names are specified without the XC prefix,
                      as for XACTstep.
  -f,--family=FAMILY  specify the chip family so that the correct set of
                      chip-specific gates is loaded. required if --part is
                      omitted. supported values: 2000 3000 5200
  -o,--output=FILE    output XNF file
  -q,--quiet          suppress INFO messages
```

because Icarus Verilog doesn't create IO pads but XACTstep needs them for off-chip signals, the whole thing is usually
invoked as:

```
java -jar xsynth.jar convert -m -p 2064pd48-50 -o foo.xnf foo.blif foo.io
```

...where `foo.blif` if generated by `iverilog -t blif -o foo.blif foo.v` and `foo.io` contains the IO pad descriptions
(see below). the resulting `foo.xnf` can then be compiled using XACTstep like any other XNF design (using `xrun`, or
by running XACTstep in DOSBox). `2064pd48-50` is the XC2064 in a PDIP-48 package (yes, that's an FPGA in a PDIP there!)
and in the `-50` speed grade. the speed grade is always separated with a dash.

valid packages and speed grades are most easily determined by starting XACTstep in DOSBox, selecting a part using the
`part 2064pd48` command (or `part` and picking from the menu) and then using `speed` to open the speed grade menu. of
course the speed grade is also printed on the chip: either in the first line after the chip name, or on the last line
after about 1995. (the package code is the first few characters of the second line, but looking at the chip it should
be pretty obvious what package it's in ;)

# IO pad description (the `*.io` file)

the `*.io` file uses proprietary extension to the BLIF format. that is, the declarations can technically also be in the
BLIF input file. using a separate file is easier to do in a build system though, and `--merge` effectively makes merges
the two physical files into a single logical file anyway. (`--merge` doesn't work with multiple `iverilog` generated
files though, because `iverilog` writes several constant signals to its BLIF file and these would then conflict.)

## I/O pads

IO pads are specified as `.pad pin i=input o=output t=tristate flag`. `pin` is the pin name as described in the
datasheet, usually something like `P4` in PDIP, PLCC or QFP packages, but alphanumeric for PGA. the connected signals
are specified like BLIF gate connections, ie. `i=input` connects the signal `input` to gate port `i` (or in this case,
the signal coming from the pin). note that BLIF signal names are case-sensitive; the IO pad's ports and flags aren't.

+-------------------+---------------------------------------+--------------+---------------+---------------------------+
| pin type          | syntax                                | input signal | output signal | tristate control signal   |
+-------------------+---------------------------------------+--------------+---------------+---------------------------+
| input only        | `.pad Px i=input`                     | `input`      | none          | logic 1 (driver disabled) | 
| output only       | `.pad Px o=output`                    | none         | `output`      | logic 0 (driver enabled)  |
| tristate output   | `.pad Px o=output t=tristate`         | none         | `output`      | `tristate`                |
| tristate IO       | `.pad Px i=input o=output t=tristate` | `input`      | `output`      | `tristate`                |
| open-drain output | `.pad Px o=output t=output`           | none         | `output`      | `output`                  |
| open-drain IO     | `.pad Px i=input o=output t=output`   | `input`      | `output`      | `output`                  |
+-------------------+---------------------------------------+--------------+---------------+---------------------------+

the tristate control signal `t` controls whether the output drivers are enabled. it is active high, that is, the output
drivers are disabled if the tristate control signal is high, and enabled if the tristate control signal is low. it can
also be seen as an active-low output enable signal. (this simply follows the behavior of the on-chip output buffers.)

an open-drain output is specified by connecting the output value `o` and tristate control `t` to the same signal: if
that signal is low, the output driver is enabled and drives low. it the signal is high, the output driver is disabled
and the output value simply doesn't matter.

to get an active low tristate control, or equivalently an active high output enable signal, invert the signal using an
inverter in BLIF or by just inverting it in Verilog. an inverting gate in BLIF, which outputs `nOE` from `OE`, is:

```
.names OE nOE
0 1
```

specifying an input on an always-enabled output (`.pad Px i=input o=output`) is possible but silly: it will always read
back the output value (unless the output is overdriven, but drive conflicts should be avoided in any design). XSynth
emits a warning for such silliness. specifying tristate but no output value (`.pad Px i=input t=tristate`) is turned
into an open-drain output, along with an accompanying warning.

the valid flags are as documented in the device datasheet:

+----------------------+--------+----------+--------+-------------------------------------------------------------+
| flags                | XC2000 | XC3000   | XC5200 | function                                                    |
+----------------------+--------+----------+--------+-------------------------------------------------------------+
| `fast`, `slow`       | —      | ✓        | ✓      | sets output slew rate; `slow` is the default                |
| `pullup`, `pulldown` | —      | `pullup` | ✓      | enable pullup / pulldown resistor on the pin (default none) |
+----------------------+--------+----------+--------+-------------------------------------------------------------+

here, XC3000 stands for the the XC3000, XC3100, XC3000A and XC3100A families.

## in-line buffering of signals

a signal can be buffered by listing it in a proprietary `.buffer` statement. the available buffer types are described
in the respective datasheet, but BUFG is always supported and maps to whatever buffer is available on the chip.

```
.buffer bufg foo bar baz
```

multiple signals can be specified on a single line (as above). XSynth takes care to insert a buffer of the indicated
type between the signal's driver and the internal net dring other gates. you want to do this if XACTstep complains that
a clock signal isn't buffered, and probably also for the primary clock signal. note that without `--merge` only signals
defined in the same BLIF file can be buffered. also note that each chip has a finite number of such buffers; XACTstep
will abort if you specify too many.

## on-chip peripherals

on-chip peripherals are specified using the BLIF `.gate` syntax with some extensions: they can use flags like IO pads,
and can have any number of outputs, including none. also, the order of arguments is free; the output doesn't have to be
specified last. all chips have some form os oscillator; see below.

the XC5200 has some additional peripherals: readback can be customied with ther RDBK symbol; startup with the STARTUP
symbol. the BSCAN symbol has to be used to enable JTAG after configuration, and also allows routing JTAG signals. these
peripherals are invoked as `.gate rdbkstartup clk=startclk` and should behave as documented in the datasheet. this
means that RDBK and BSCAN have implicit connections if the corresponding pin isn't otherwise connected: RDBK.TRIG
defaults to MD0, RDBK.DATA to MD1, while BSCAN wires TDI, TDO, TMS, and TCK to the correspondingly names pins by
default. this means that to enable JTAG, `.gate bscan` is generally all that is required.

finally, there is the `latchclock` gate for connecting the implicit clock used by BLIF when no clock is specified for a
latch. this is never generated by Icarus Verilog, but if used by a BLIF file from some other source, it can be connected
as `.gate latchclock c=clock`. this would make `.latch foo bar` equivalent to `.latch foo bar re clock`.

## on-chip oscillator

all of those old chips have some sort of on-chip oscillator, though it's implemented differently in each family. for
chips with off-chip oscillators (XC2000, XC3x00, XC3x00A families), the oscillator has to be placed in the design but
also needs to be enabled with `makebits -s1` or `-s2`. `-s1` uses the input as is, while `-s2` divides it by 2 to
guarantee 50/50 duty cycle (and obviously also half the frequency).

the XC2000 family has an integrated crystal oscillator, which can be invoked as `.gate osc o=clock`. because the output
of the oscillator *must* use the ACLK buffer, the signal is always fed through this buffer automatically. specifying
`.buffer ACLK clock` explicitly is harmless, but attempting to buffer another signal using ACLK will result in an error
from XACTstep: the chip only has one ACLK buffer.

another peculiarity of the XC2000 family as that ACLK can only be driven from the oscillator output pad. thus while any
signal can be buffered using ACLK, XACTstep will bring it out on that pad to drive the ACLK buffer. the upshot is that
if the ACLK buffer is used, the oscillator output pad will always output the clock buffered by ACLK. this isn't
necessarily a bad thing, since that's probably the system clock, but it means that the pin is primarily a dedicated-
function pin that cannot be used for unrestricted user IO.

the XC3x00/XC3x00A families have a similar on-chip crystal oscillator, but it doesn't need to use the ACLK buffer. as
such, `.gate osc o=clock` generates the `clock` output without a clock buffer by default, which XACTstep will probably
complain about. specify an explicit `.buffer ACLK clock` (or `.buffer BUFG clock`) to buffer the signal.

the XC5200 family doesn't have an off-chip oscillator. instead it contains an on-chip 12MHz RC oscillator, and a
programmable divider which can be driven from either the RC oscillator or a user-supplied clock. that user-supplied
clock can obvioulsy come from an IO pad, but you can't connect a crystal directly to the FPGA. the oscillator is
invoked as `.gate osc52 div4=clock1 div1024=clock2`, generating a 3MHz `clock1` and a 11.7kHz `clock2` in this case.
the divider is used by connecting the clock input `c` as in `.gate osc52 c=clkin div16=clock1 div2=clock2`.

this somewhat follows to the XNF specification and is therefore different from the datasheet. the logical outputs
`divN` are automatically allocated to one of the physical outputs `OSC1` and `OSC2`, with dividers configured
appropriately. `OSC1` can divide by 4, 16, 64, or 256; `OSC2` by 2, 8, 32, 128, 1024, 4096, 16384, or 65536. the ratios
do not overlap, which means that using `DIV4` and `DIV16` at the same time is an error: both would ahve to use OSC1,
but that output can only divide by either 4 or 16, not both.

## restrictions

- the XSynth BLIF parser doesn't handle subcircuits or state machines. subcircuits could be added relatively easily,
  but aren't needed for use with Icarus Verilog because its `tgt-blif` always produces flattened models without
  subcircuits. BLIF state machines aren't particularly useful; they can be implemented just as well in Verilog.
- Icarus Verilog's `tgt-blif` does not support processes (`always @(!reset or posedge clock)`). the problem with
  `!reset` can be worked around by simply omitting it: as long as the clock is running in reset and reset is longer
  than a clock cycle (a common restriction), `always @(posedge clock) if (!reset) ... else ...` behaves exactly like
  `always @(!reset or posedge clock)  if (!reset) ... else ...`. also, flipflops reliably clear to zero in an FPGA, so
  most intitalization can instead be done with the FPGA's global reset signal.
- Icarus Verilog's `tgt-blif` only ever generates flipflops (no latches). not using the latch capability may be a
  problem for the XC2000 family which is short on resources anyway. for most synchronous designs, flipflops should be
  enough. the XC3x00 / XC3x00A families don't even support latches at all, except for the input latches.
- there is no explicit support for IO flipflops and latches. grafting them onto apparently combinatorial Verilog signals
  isn't very intuitive, so they would have to be autodetected.
- similarly, the "gate enable" and reset / preset inputs of flipflops aren't supported because there is no way to
  specify them in BLIF. they would also have to be determined from the gate connected to the data input.

# alternatives

## Icarus Verilog circa 0.7 / 0.8 with `tgt-fpga`

Icarus Verilog 0.8 complains that it's missing an implementation of LPM_ADD in `tgt-fpga`. Icarus Verilog 0.7 instead
appears to be fairly picky about the Verilog syntax that it accepts, and didn't accept the syntax in the test file.
those two version would be mostly period-accurate, though, and should work quite well within their limitations.

## `synthx` from the XACTstep ABEL package

`synthx` is a tool that comes with XACTstep 5.2.0, part of the ABEL package (ds371), which is also supposed to translate
BLIF into XNF. it doesn't readily read BLIF files not created by its own AHDL compiler, and no version that is around
seems to actually create an output XNF file. if it did, it would go far beyond XSynth by supporting state machines, but
on the other hand isn't intended to produce a standalone design. in particular, the ABEL toolchain isn't very flexible
wrt adding IO pads, because the user is supposed to do that in a higher-level schematic entry tool.

## ViewLogic WorkView CAD as included in XACTstep 5.0.0

this is the period-accurate way, using schematic entry under DOS. as such, it's great for the historical exprience, and
a really tedious way to design logic. unfortunately it doesn't seem to work in DOSBox, at least not without additional
work.