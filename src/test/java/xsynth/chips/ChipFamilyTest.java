package xsynth.chips;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import xsynth.blif.CustomGateFactory;

public class ChipFamilyTest {
	@Test
	public void testGetFamilyByPartName() {
		assertInstanceOf(XC2000Family.class, ChipFamily.forPart("2064pd48-50"));
		assertInstanceOf(XC2000Family.class, ChipFamily.forPart("2018TQ100-130"));
		assertInstanceOf(XC3000Family.class, ChipFamily.forPart("3030pc84-70"));
		assertInstanceOf(XC3000Family.class, ChipFamily.forPart("3030AVQ100-7"));
		assertInstanceOf(XC3000Family.class, ChipFamily.forPart("3195APC84-2"));
		assertInstanceOf(XC3000Family.class, ChipFamily.forPart("3020"));
		assertInstanceOf(XC5200Family.class, ChipFamily.forPart("5202PQ100-5"));
		assertInstanceOf(XC4000Family.class, ChipFamily.forPart("4003PQ100-5"));
	}

	private void assertInstanceOf(final Class<? extends ChipFamily> clazz, final ChipFamily family) {
		assertTrue(clazz.isAssignableFrom(family.getClass()));
	}

	@ParameterizedTest
	@MethodSource("getFamilies")
	public void testFamilyHasIoPad(final ChipFamily family) {
		assertNotNull(family.getCustomGates());
		assertTrue(family.getCustomGates().containsKey(CustomGateFactory.IOPAD_GATE));
	}

	public static List<ChipFamily> getFamilies() {
		return ChipFamily.FAMILIES;
	}
}
