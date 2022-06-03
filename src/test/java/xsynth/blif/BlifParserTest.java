package xsynth.blif;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import xsynth.Diagnostics.AbortedException;
import xsynth.DiagnosticsShim;
import xsynth.blif.SumOfProducts.Product;
import xsynth.blif.SumOfProducts.ProductTerm;

public class BlifParserTest {
	private DiagnosticsShim diag;
	private BlifModel model;

	@Test
	public void testEmptyFile() throws IOException, AbortedException {
		// empty file would naturally parse into a list of 0 models, but that gives all
		// sorts of boundary conditions because then there is no primary model. it's
		// never what was intended, so just throw an exception instead
		assertParseDiagnostics(1, 0, 0, "");
		// "empty" means "no statements", not actually an empty file
		assertParseDiagnostics(1, 0, 0, "# still nothing", " # a lot of text for sure", "#but no code", "   \\", "\t");
	}

	@Test
	public void testModelHeader() throws IOException, AbortedException {
		parse(".model foo", ".inputs a b c", ".outputs d e f", ".clock g h");
		assertNotNull(model);
		assertEquals("foo", model.getName());
		// clocks are implicitly inputs
		assertEquals(Set.of("a", "b", "c", "g", "h"), model.getInputs());
		assertEquals(Set.of("d", "e", "f"), model.getOutputs());
		assertEquals(Set.of("g", "h"), model.getClocks());
		assertEquals(Map.of(), model.getBuffers());
		diag.assertNoMessages();

		// empty statements are syntactically ok, as is omitting them entirely
		parse(".model foo", ".inputs", ".clock clk", ".buffer bufg");
		assertNotNull(model);
		assertEquals("foo", model.getName());
		assertEquals(Set.of("clk"), model.getInputs());
		assertEquals(Set.of(), model.getOutputs());
		assertEquals(Set.of("clk"), model.getClocks());
		// an empty list of buffers is actually very useful for declaring that the
		// clocks are *not* meant to be implicitly buffered
		assertEquals(Map.of("bufg", Set.of()), model.getBuffers());
		diag.assertNoMessages();

		// inputs and clocks can overlap. this isn't how berkeley-abc does it, but it's
		// easier for transforming to various output netlists
		parse(".model foo", ".inputs clk klc", ".clock clk kcl", ".buffer bufg clk");
		assertNotNull(model);
		assertEquals("foo", model.getName());
		assertEquals(Set.of("clk", "klc", "kcl"), model.getInputs());
		assertEquals(Set.of(), model.getOutputs());
		assertEquals(Set.of("clk", "kcl"), model.getClocks());
		// this is incidentially an example of bufg use: it makes the clk clock
		// buffered, but not the kcl clock
		assertEquals(Map.of("bufg", Set.of("clk")), model.getBuffers());
		diag.assertNoMessages();
	}

	@Test
	public void testMultipleModels() throws IOException, AbortedException {
		final Map<String, BlifModel> models = parse(".model foo", ".end", ".model bar", ".model baz");
		final BlifModel foo = models.get("foo");
		final BlifModel bar = models.get("bar");
		final BlifModel baz = models.get("baz");
		// all models detected
		assertNotNull(foo);
		assertNotNull(bar);
		assertNotNull(baz);
		// primary model is simply the first one in the file
		assertEquals(foo, model);
		assertNotEquals(foo, bar);
		assertNotEquals(foo, baz);
		assertNotEquals(bar, baz);
		// names are correct (though we kind-of already tested that by fetching them by
		// name)
		assertEquals("foo", foo.getName());
		assertEquals("bar", bar.getName());
		assertEquals("baz", baz.getName());

		for (final BlifModel model : List.of(foo, bar, baz)) {
			assertEquals(Set.of(), model.getInputs());
			assertEquals(Set.of(), model.getOutputs());
			assertEquals(Set.of(), model.getClocks());
			assertEquals(Map.of(), model.getBuffers());
		}
		diag.assertNoMessages();
	}

	@Test
	public void testUnnamedModels() throws IOException, AbortedException {
		final Map<String, BlifModel> models = parse(".names a b c", "11 1", ".end", ".names a", "0");
		assertEquals(2, models.size());
		final BlifModel second = models.values().stream().filter(m -> m != model).findFirst().orElseThrow();
		// all models detected
		assertNotNull(model);
		assertNotNull(second);
		// unnamed models get implicit names derived from the filename
		assertEquals("test", model.getName());
		assertEquals("test/model1", second.getName());
		// 1 warning: "implicitly named submodel"
		// 1 info: "implicitly named primary model"
		diag.assertNumMessages(0, 1, 1);
	}

	@Test
	public void testInvalidModelHeader() throws IOException, AbortedException {
		// too many model names
		assertParseDiagnostics(1, 0, 0, ".model foo bar baz");
		// model name missing
		assertParseDiagnostics(1, 0, 0, ".model ");
		// buffer type missing
		assertParseDiagnostics(1, 0, 0, ".model foo", ".buffer ");

		// junk after .end (and also outside a model, but the error overrides the
		// warning)
		assertParseDiagnostics(1, 0, 0, ".end all existence");
		// .end outside a model (twice, actually). that's not an error, though, only a
		// warning
		assertParseDiagnostics(0, 2, 0, ".end", ".model foo", ".end", ".end");
	}

	@Test
	public void testSumOfProducts() throws IOException, AbortedException {
		// simple example
		SumOfProducts sop = parseGate(SumOfProducts.class, ".names a b c", "11 1");
		assertNames(sop, "c", "a", "b");
		assertEquals(1, sop.getTerms().size());
		assertProductTerm(sop, 0, '1', "a", "b");
		diag.assertNoMessages();

		// complex example:
		// - don't cares
		// - zero output bit
		// - spaces between input bits
		sop = parseGate(SumOfProducts.class, ".names a b c x", "111 1", "0-- 1", "1-0 0", "- - 1 1");
		assertNames(sop, "x", "a", "b", "c");
		assertEquals(4, sop.getTerms().size());
		assertProductTerm(sop, 0, '1', "a", "b", "c");
		assertProductTerm(sop, 1, '1', "!a");
		assertProductTerm(sop, 2, '0', "a", "!c");
		assertProductTerm(sop, 3, '1', "c");
		diag.assertNoMessages();
	}

	@Test
	public void testConstants() throws IOException, AbortedException {
		// constant one
		SumOfProducts sop = parseGate(SumOfProducts.class, ".names a", "1");
		assertNames(sop, "a");
		assertEquals(1, sop.getTerms().size());
		assertProductTerm(sop, 0, '1');
		diag.assertNoMessages();

		// two ways of specifying a constant zero: explicit zero output, or simply no
		// conditions that could be true
		sop = parseGate(SumOfProducts.class, ".names a", "0");
		assertNames(sop, "a");
		assertEquals(1, sop.getTerms().size());
		assertProductTerm(sop, 0, '0');
		diag.assertNoMessages();
		sop = parseGate(SumOfProducts.class, ".names a");
		assertNames(sop, "a");
		assertEquals(0, sop.getTerms().size());
		diag.assertNoMessages();
	}

	private void assertNames(final BlifGate gate, final String output, final String... inputs) {
		assertEquals(Set.of(output), new HashSet<>(gate.getOutputs()));
		assertEquals(Set.of(inputs), new HashSet<>(gate.getInputs()));
	}

	private void assertProductTerm(final SumOfProducts sop, final int j, final char out, final String... in) {
		final Product prod = sop.getTerms().get(j);
		assertEquals(out == '0', prod.isInvertOutput());
		final List<ProductTerm> terms = prod.getTerms();
		assertEquals(in.length, terms.size());

		for (int i = 0; i < in.length; i++) {
			final String name = in[i];
			final ProductTerm term = terms.get(i);
			if (name.startsWith("!")) {
				assertTrue(term.isInvertInput());
				assertEquals(name.substring(1), term.getInput());
			} else {
				assertFalse(term.isInvertInput());
				assertEquals(name, term.getInput());
			}
		}
	}

	@Test
	public void testInvalidSumOfProducts() throws IOException, AbortedException {
		// wrong number of bits for names
		assertParseDiagnostics(1, 0, 0, ".model foo", ".names a b c", "1 1");
		assertParseDiagnostics(1, 0, 0, ".model foo", ".names a b c", "11111 1");
		// illegal input or output bit
		assertParseDiagnostics(1, 0, 0, ".model foo", ".names a b c", "11 -");
		assertParseDiagnostics(1, 0, 0, ".model foo", ".names a b c", "1? 1");
		// illegal formatting; the space before the output cover is mandatory
		assertParseDiagnostics(1, 0, 0, ".model foo", ".names a b c", "111");
	}

	@Test
	public void testLatches() throws IOException, AbortedException {
		// simple example
		Latch latch = parseGate(Latch.class, ".latch in out re clk 3");
		assertNames(latch, "out", "in", "clk");
		assertEquals("in", latch.getDataInput());
		assertEquals("out", latch.getDataOutput());
		assertEquals("clk", latch.getClockInput());
		assertEquals(LatchType.re, latch.getType());
		assertEquals(LatchInitialValue.UNKNOWN, latch.getInitialValue());
		diag.assertNoMessages();

		// clock is optional
		latch = parseGate(Latch.class, ".latch in out 3");
		assertNames(latch, "out", "in");
		assertEquals("in", latch.getDataInput());
		assertEquals("out", latch.getDataOutput());
		assertNull(latch.getClockInput());
		assertEquals(LatchType.re, latch.getType());
		assertEquals(LatchInitialValue.UNKNOWN, latch.getInitialValue());
		diag.assertNoMessages();
		// and so is the initial value
		latch = parseGate(Latch.class, ".latch in out fe ck");
		assertNames(latch, "out", "in", "ck");
		assertEquals("in", latch.getDataInput());
		assertEquals("out", latch.getDataOutput());
		assertEquals("ck", latch.getClockInput());
		assertEquals(LatchType.fe, latch.getType());
		assertEquals(LatchInitialValue.UNKNOWN, latch.getInitialValue());
		diag.assertNoMessages();
		// or even both
		latch = parseGate(Latch.class, ".latch in out ");
		assertNames(latch, "out", "in");
		assertEquals("in", latch.getDataInput());
		assertEquals("out", latch.getDataOutput());
		assertNull(latch.getClockInput());
		assertEquals(LatchType.re, latch.getType());
		assertEquals(LatchInitialValue.UNKNOWN, latch.getInitialValue());
		diag.assertNoMessages();
	}

	@ParameterizedTest
	@MethodSource("getLatchTypes")
	public void testLatchTypes(final String name, final LatchType type) throws IOException, AbortedException {
		// simple example
		final Latch latch = parseGate(Latch.class, ".latch in out " + name + " clk 3");
		assertNames(latch, "out", "in", "clk");
		assertEquals("in", latch.getDataInput());
		assertEquals("out", latch.getDataOutput());
		assertEquals("clk", latch.getClockInput());
		assertEquals(type, latch.getType());
		assertEquals(LatchInitialValue.UNKNOWN, latch.getInitialValue());
		diag.assertNoMessages();
	}

	public static Object[][] getLatchTypes() {
		return new Object[][] { //
				{ "re", LatchType.re }, //
				{ "fe", LatchType.fe }, //
				{ "ah", LatchType.ah }, //
				{ "al", LatchType.al }, //
				{ "as", LatchType.as } };
	}

	@ParameterizedTest
	@MethodSource("getLatchInitialValues")
	public void testLatchInitialValues(final String name, final LatchInitialValue init)
			throws IOException, AbortedException {
		// simple example
		final Latch latch = parseGate(Latch.class, ".latch in out as clk " + name);
		assertNames(latch, "out", "in", "clk");
		assertEquals("in", latch.getDataInput());
		assertEquals("out", latch.getDataOutput());
		assertEquals("clk", latch.getClockInput());
		assertEquals(LatchType.as, latch.getType());
		assertEquals(init, latch.getInitialValue());
		diag.assertNoMessages();
	}

	public static Object[][] getLatchInitialValues() {
		return new Object[][] { //
				{ "0", LatchInitialValue.RESET }, //
				{ "1", LatchInitialValue.PRESET }, //
				{ "2", LatchInitialValue.DONTCARE }, //
				{ "3", LatchInitialValue.UNKNOWN } };
	}

	@Test
	public void testInvalidLatches() throws IOException, AbortedException {
		// (input or) output missing
		assertParseDiagnostics(1, 0, 0, ".model test", ".latch");
		assertParseDiagnostics(1, 0, 0, ".model test", ".latch in");
		// undefined latch type, or clock missing
		assertParseDiagnostics(1, 0, 0, ".model test", ".latch in out ae clk");
		assertParseDiagnostics(1, 0, 0, ".model test", ".latch in out ae");
		// undefined initial value
		assertParseDiagnostics(1, 0, 0, ".model test", ".latch in out re clk 4");
		assertParseDiagnostics(1, 0, 0, ".model test", ".latch in out 5");
		// trailing garbage
		assertParseDiagnostics(1, 0, 0, ".model test", ".latch in out re clk 1 important");
		assertParseDiagnostics(1, 0, 0, ".model test", ".latch in out important");
	}

	@Test
	public void testDuplicateDriver() throws IOException, AbortedException {
		// there's really very little to test here. two gates driving the same output,
		// and that's it
		assertParseDiagnostics(1, 0, 0, ".model test", ".latch in out re clk 3", ".names in1 in2 out", "11 1");
		// maybe test duplicate inputs; these have to work just find
		assertParseDiagnostics(0, 0, 0, ".model test", ".latch in out1 re clk 3", ".names in in out2", "11 1");
		// .clock cannot be driven by the model itself
		assertParseDiagnostics(1, 0, 0, ".model test", ".clock out", ".names out");
	}

	@Test
	public void testInferredIO() throws IOException, AbortedException {
		// if input or output omitted, it is inferred from the signals that are
		// "missing" ie. not used by the circuit
		parse(".model test", ".latch in out re clk 3", ".names a b c d e");
		assertEquals(Set.of("in", "clk", "a", "b", "c", "d"), model.getInputs());
		assertEquals(Set.of("out", "e"), model.getOutputs());
		assertEquals(Set.of(), model.getClocks()); // clock isn't inferred
		diag.assertNoMessages();

		// slightly more complicated case where where not just every single signal is an
		// input or output. (this model doesn't actually have outputs, but neither does
		// the toplevel circuit if all signals are tied to .pad's)
		parse(".model test", ".latch in out re clk 3", ".names out x y in");
		assertEquals(Set.of("clk", "x", "y"), model.getInputs());
		assertEquals(Set.of(), model.getOutputs());
		assertEquals(Set.of(), model.getClocks());
		diag.assertNoMessages();

		// if only the clock is specified, then that clock becomes an input, but the
		// inputs and outputs are still inferred properly
		parse(".model test", ".clock clk", ".latch in out re clk 3", ".names a b c d e");
		assertEquals(Set.of("in", "clk", "a", "b", "c", "d"), model.getInputs());
		assertEquals(Set.of("out", "e"), model.getOutputs());
		assertEquals(Set.of("clk"), model.getClocks());
		diag.assertNoMessages();

		// if inputs specify a subset, a warning is generated per pin, and the pin is
		// pulled low. if a pin is specified as a clock, that is treated as an input and
		// thus doesn't give the warning.
		// if outputs specify a subset, only an INFO message is generated (a single one
		// listing all unused outputs)
		parse(".model test", ".inputs a b c", ".outputs e", ".clock clk", ".latch in out re clk 3", ".names a b c d e");
		assertEquals(Set.of("a", "b", "c", "clk"), model.getInputs());
		assertEquals(Set.of("e"), model.getOutputs());
		assertEquals(Set.of("clk"), model.getClocks());
		diag.assertNumMessages(0, 2, 1);
	}

	private void assertParseDiagnostics(final int errors, final int warnings, final int infos, final String... lines) {
		if (errors > 0)
			assertThrows(AbortedException.class, () -> parse(lines));
		else
			assertDoesNotThrow(() -> parse(lines));
		diag.assertNumMessages(errors, warnings, infos);
	}

	@SuppressWarnings("unchecked")
	private <T extends BlifGate> T parseGate(final Class<T> cls, final String... lines)
			throws IOException, AbortedException {
		final String[] temp = new String[lines.length + 1];
		temp[0] = ".model test";
		System.arraycopy(lines, 0, temp, 1, lines.length);
		parse(temp);

		assertNotNull(model);
		assertEquals(1, model.getGates().size());
		final BlifGate gate = model.getGates().get(0);
		assertEquals(cls, gate.getClass());
		return (T) gate;
	}

	private Map<String, BlifModel> parse(final String... lines) throws IOException, AbortedException {
		diag = new DiagnosticsShim();
		try (ByteArrayInputStream data = new ByteArrayInputStream(
				String.join("\n", lines).getBytes(StandardCharsets.UTF_8))) {
			final BlifParser parser = new BlifParser(diag, Map.of());
			model = parser.parse(data, "/opt/xsynth/test.blif");
			return parser.getModels();
		}
	}
}
