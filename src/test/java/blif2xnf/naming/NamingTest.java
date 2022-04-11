package blif2xnf.naming;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class NamingTest {
	@Test
	public void testUniqueGlobalNames() {
		final Names names = new Names(false);
		final Name a = names.getGlobal("test$");
		final Name b = names.getGlobal("Test-");
		final Name c = names.getGlobal("foo");
		final Name d = names.getGlobal("_FOO");
		names.resolve();
		// unique without qualifier, so they stay unique
		assertEquals("test$", a.getXnf());
		assertEquals("Test_", b.getXnf());
		assertEquals("foo", c.getXnf());
		assertEquals("_FOO", d.getXnf());
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
		final Global a = names.getGlobal("test$");
		final Global b = names.getGlobal("Test~");
		final Global c = names.getGlobal("TEST");
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
		assertEquals("test$-1G/AND", d.getXnf());
		assertEquals("test$-1G/AND1", e.getXnf());
		assertEquals("test$-1G/AND2", f.getXnf());
		assertEquals("Test$-HE/OR", g.getXnf());
		assertEquals("Test$-HE/IPAD", h.getXnf());
		assertEquals("TEST/IPAD", i.getXnf());
	}

	@Test
	public void testQualifiedNameCollision() {
		final Names names = new Names(false);
		final Global a = names.getGlobal("TEST");
		final Global b = names.getGlobal("TEST/IPAD");
		final Global c = names.getGlobal("TEST/IPAD1");
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
}
