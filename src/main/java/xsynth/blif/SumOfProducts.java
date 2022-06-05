package xsynth.blif;

import java.util.ArrayList;
import java.util.List;

public class SumOfProducts implements BlifGate {
	private final List<Product> terms = new ArrayList<>();
	private final String outputName;
	private final List<String> inputNames;

	public SumOfProducts(final String outputName, final List<String> inputNames) {
		this.outputName = outputName;
		this.inputNames = inputNames;
	}

	@Override
	public List<String> getOutputs() {
		return List.of(outputName);
	}

	@Override
	public List<String> getInputs() {
		return inputNames;
	}

	public String getOutput() {
		return outputName;
	}

	void addProductTerm(final char output, final char... inputs) throws IllegalArgumentException {
		if (inputs.length != inputNames.size())
			throw new IllegalArgumentException(
					inputs.length + 1 + " bits for " + (inputNames.size() + 1) + " names (must match)");
		if (output != '1' && output != '0')
			throw new IllegalArgumentException("illegal output bit '" + output + "'");

		final Product product = new Product(output == '0');
		for (int i = 0; i < inputs.length; i++)
			switch (inputs[i]) {
			case '0' -> product.addTerm(true, inputNames.get(i));
			case '1' -> product.addTerm(false, inputNames.get(i));
			case '-' -> { // ignored
			}
			default -> throw new IllegalArgumentException("illegal input bit '" + inputs[i] + "'");
			}
		terms.add(product);
	}

	public List<Product> getTerms() {
		return terms;
	}

	@Override
	public String toString() {
		return "SumOfProducts[" + outputName + ", " + inputNames.toString().substring(1);
	}

	public class Product {
		private final List<ProductTerm> terms = new ArrayList<>();
		private final boolean invertOutput;

		private Product(final boolean invertOutput) {
			this.invertOutput = invertOutput;
		}

		private void addTerm(final boolean invertInput, final String name) {
			terms.add(new ProductTerm(invertInput, name));
		}

		public List<ProductTerm> getTerms() {
			return terms;
		}

		public boolean isInvertOutput() {
			return invertOutput;
		}
	}

	public class ProductTerm {
		private final boolean invertInput;
		private final String input;

		private ProductTerm(final boolean invertInput, final String input) {
			this.invertInput = invertInput;
			this.input = input;
		}

		public String getInput() {
			return input;
		}

		public boolean isInvertInput() {
			return invertInput;
		}
	}
}
