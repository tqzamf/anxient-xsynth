package xsynth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

public class DiagnosticsShim extends Diagnostics {
	private final List<String> messages = new ArrayList<>();
	private int nInfo, nWarn, nError;

	@Override
	protected void print(final String level, final SourceLocation sloc, final List<String> line, final String message) {
		messages.add(message);
	}

	@Override
	public void info(final SourceLocation sloc, final List<String> line, final String message) {
		nInfo++;
		super.info(sloc, line, message);
	}

	@Override
	public void warn(final SourceLocation sloc, final List<String> line, final String message) {
		nWarn++;
		super.warn(sloc, line, message);
	}

	@Override
	public AbortedException error(final SourceLocation sloc, final List<String> line, final String message) {
		nError++;
		return super.error(sloc, line, message);
	}

	public void assertNumMessages(final int errors, final int warnings, final int infos) {
		assertEquals(errors, nError);
		assertEquals(warnings, nWarn);
		assertEquals(infos, nInfo);
	}

	public void assertNoMessages() {
		assertNumMessages(0, 0, 0);
	}
}
