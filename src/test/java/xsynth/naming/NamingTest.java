package xsynth.naming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class NamingTest {
	@Test
	public void testUniqueGlobalNames() {
		final Names names = new Names(false);
		final Name a = names.getGlobal("test$");
		final Name b = names.getGlobal("Test-");
		final Name c = names.getGlobal("foo");
		final Name d = names.getGlobal("_FOO");
		final Name e = names.getGlobal("1234");
		names.resolve();
		// unique without qualifier, so they stay unique
		assertEquals("test$", a.getXnf());
		assertEquals("Test_", b.getXnf());
		assertEquals("foo", c.getXnf());
		assertEquals("_FOO", d.getXnf());
		// all-numeric identifier has a dash appended, making it no longer all-numeric
		assertEquals("1234-", e.getXnf());
	}

	@Test
	public void testCollidingGlobalNames() {
		final Names names = new Names(false);
		final Name a = names.getGlobal("test$");
		final Name b = names.getGlobal("Test~");
		final Name c = names.getGlobal("foo");
		final Name d = names.getGlobal("FOO");
		names.resolve();
		// names map to the same string, so they get a qualifier
		assertEquals("test$-1G", a.getXnf());
		assertEquals("Test$-HE", b.getXnf());
		assertEquals("foo-0", c.getXnf());
		assertEquals("FOO-W", d.getXnf());
	}

	@Test
	public void testQualifiedNames() {
		final Names names = new Names(false);
		final GlobalName a = names.getGlobal("test$");
		final GlobalName b = names.getGlobal("Test~");
		final GlobalName c = names.getGlobal("TEST");
		final Name d = names.getAnonymous(a, "AND");
		final Name e = names.getAnonymous(a, "AND");
		final Name f = names.getAnonymous(a, "AND");
		final Name g = names.getAnonymous(b, "OR");
		final Name h = names.getAnonymous(b, "IPAD");
		final Name i = names.getAnonymous(c, "IPAD");
		names.resolve();
		// anonymous names use the qualified form of the global name they're based on
		assertEquals("test$-1G", a.getXnf());
		assertEquals("Test$-HE", b.getXnf());
		assertEquals("TEST", c.getXnf());
		assertEquals("Test$-HE/OR", g.getXnf());
		assertEquals("Test$-HE/IPAD", h.getXnf());
		assertEquals("TEST/IPAD", i.getXnf());
		// in case of duplicate anonymous names, the counter increments
		assertEquals("test$-1G/AND", d.getXnf());
		assertEquals("test$-1G/AND1", e.getXnf());
		assertEquals("test$-1G/AND2", f.getXnf());
		// different instances with the same base name and qualifier get different names
		assertNotEquals(d.getXnf(), e.getXnf());
		assertNotEquals(d.getXnf(), f.getXnf());
		assertNotEquals(e.getXnf(), f.getXnf());
	}

	@Test
	public void testQualifiedNameCollision() {
		final Names names = new Names(false);
		final GlobalName a = names.getGlobal("TEST");
		final GlobalName b = names.getGlobal("TEST/IPAD");
		final GlobalName c = names.getGlobal("TEST/IPAD1");
		final Name d = names.getAnonymous(a, "IPAD");
		final Name e = names.getAnonymous(a, "OPAD");
		names.resolve();
		// anonymous names simply increment if they collide with a global name
		assertEquals("TEST", a.getXnf());
		assertEquals("TEST/IPAD", b.getXnf());
		assertEquals("TEST/IPAD1", c.getXnf());
		assertEquals("TEST/IPAD2", d.getXnf());
		assertEquals("TEST/OPAD", e.getXnf());
	}

	@Test
	public void testSpecialNameCollision() {
		final Names names = new Names(false);
		final GlobalName a = names.getGlobal("VCC");
		final GlobalName b = names.getGlobal("GND");
		final GlobalName c = names.getGlobal("GND_1");
		final Name d = names.getSpecial(SpecialName.VCC);
		final Name e = names.getSpecial(SpecialName.VCC);
		final Name f = names.getSpecial(SpecialName.GND);
		final Name g = names.getSpecial(SpecialName.GCLK);
		final Name h = names.getSpecial("MD1");
		names.resolve();
		// unlike anonymous names, special names are global but in their own special
		// namespace. that is, getting VCC twice yields the same network each time
		assertEquals(e.getXnf(), d.getXnf());
		// special names also simply increment if they collide with a global name
		assertEquals("VCC", a.getXnf());
		assertEquals("GND", b.getXnf());
		assertEquals("GND_1", c.getXnf());
		assertEquals("VCC_1", d.getXnf());
		assertEquals("VCC_1", e.getXnf());
		assertEquals("GND_2", f.getXnf());
		// however, as long as the input netlist doesn't use idiotic names (like GND or
		// VCC), the special nets get their "official" name
		assertEquals("GCLK", g.getXnf());
		assertEquals("MD1", h.getXnf());
	}
}
