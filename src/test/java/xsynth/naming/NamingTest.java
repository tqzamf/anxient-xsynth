package xsynth.naming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class NamingTest {
	@Test
	public void testUniqueGlobalNames() {
		final Namespace names = new Namespace(false);
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
		final Namespace names = new Namespace(false);
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
		final Namespace names = new Namespace(false);
		final Name a = names.getGlobal("test$");
		final Name b = names.getGlobal("Test~");
		final Name c = names.getGlobal("TEST");
		final Name d = a.getAnonymous("AND");
		final Name e = a.getAnonymous("AND");
		final Name f = a.getAnonymous("AND");
		final Name g = b.getAnonymous("OR");
		final Name h = b.getAnonymous("IPAD");
		final Name i = c.getAnonymous("IPAD");
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
		final Namespace names = new Namespace(false);
		final Name a = names.getGlobal("TEST");
		final Name b = names.getGlobal("TEST/IPAD");
		final Name c = names.getGlobal("TEST/IPAD1");
		final Name d = a.getAnonymous("IPAD");
		final Name e = a.getAnonymous("OPAD");
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
		final Namespace names = new Namespace(false);
		final Name a = names.getGlobal("VCC");
		final Name b = names.getGlobal("GND");
		final Name c = names.getGlobal("GND_1");
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

	@Test
	public void testHierarchicalNamespaces() {
		final Namespace root = new Namespace(false);
		final Namespace foo = root.getNamespace("foo", List.of());
		final Namespace bar = foo.getNamespace("bar", List.of());
		final Namespace baz = bar.getNamespace("baz", List.of());
		final Namespace bay = root.getNamespace("bay", List.of());
		final Name a = root.getGlobal("bay");
		final Name b = root.getGlobal("bar");
		final Name c = bar.getGlobal("baz/c");
		final Name d = baz.getGlobal("d");
		final Name vcc1 = foo.getSpecial(SpecialName.VCC);
		final Name vcc2 = bay.getSpecial(SpecialName.VCC);
		root.resolve();
		// namespaces "make way" for signal names
		assertEquals("bay", a.getXnf());
		assertEquals("bay-1", bay.getXnf());
		// and they are hierarchical, using "/" as the hierarchy separator
		assertEquals("foo", foo.getXnf());
		assertEquals("foo/bar", bar.getXnf());
		// a prefix is enough to rename the namespace
		assertEquals("foo/bar/baz/c", c.getXnf());
		assertEquals("foo/bar/baz-1", baz.getXnf());
		assertEquals("foo/bar/baz-1/d", d.getXnf());
		// names don't conflict between hierarchy levels
		assertEquals("bar", b.getXnf());
		// special nets are always created in the top namespace
		assertSame(vcc1, vcc2);
		assertTrue(root.hasSpecial(SpecialName.VCC));
		assertSame(vcc1, root.getSpecial(SpecialName.VCC));
	}

	@Test
	public void testNamespaceConflicts() {
		final Namespace root = new Namespace(false);
		final Namespace foo = root.getNamespace("foo", List.of());
		final Namespace bar1 = foo.getNamespace("b/a/r", List.of());
		final Namespace bar2 = foo.getNamespace("b/a", List.of());
		final Namespace bar3 = foo.getNamespace("b-a", List.of());
		final Namespace bar4 = foo.getNamespace("b-A", List.of());
		final Name e = bar1.getGlobal("f");
		final Name f = bar2.getGlobal("r/f");
		final Name g = bar3.getGlobal("r/f");
		final Name h = bar4.getGlobal("r/f");
		root.resolve();
		// namespace names can themselves contain slashes, but they're replaced by
		// dashes for simplicity. in the example, that avoids a conflict for foo/b/a/r/f
		assertNotEquals(e.getXnf(), f.getXnf());
		assertEquals("foo/b-a-r/f", e.getXnf());
		assertEquals("foo/b-a/r/f", f.getXnf());
		assertEquals("foo/b-a-r", bar1.getXnf());
		assertEquals("foo/b-a", bar2.getXnf());
		// namespaces use the same "qualify if non-unique" strategy as signal names
		assertEquals("foo/b_a-0", bar3.getXnf());
		assertEquals("foo/b_A-4", bar4.getXnf());
		assertEquals("foo/b_a-0/r/f", g.getXnf());
		assertEquals("foo/b_A-4/r/f", h.getXnf());
	}

	@Test
	public void testNamespacePorts() {
		final Namespace root = new Namespace(false);
		final Namespace foo = root.getNamespace("foo", List.of("b", "bar"));
		final Namespace bar1 = foo.getNamespace("b", List.of("a"));
		final Namespace bar2 = foo.getNamespace("b/a", List.of());
		final Namespace bar3 = bar1.getNamespace("a", List.of("a"));
		final Namespace bar4 = bar1.getNamespace("ar", Map.of("g", "a"));
		final Name a = foo.getGlobal("a");
		final Name b = foo.getGlobal("b");
		final Name c = bar1.getGlobal("a");
		final Name d = bar3.getGlobal("a");
		final Name e = bar1.getGlobal("b");
		final Name f = bar3.getGlobal("b");
		final Name g = bar4.getGlobal("g");
		final Name h = bar4.getGlobal("a");
		root.resolve();
		assumeTrue("foo".equals(foo.getXnf()));
		// local names are qualified by the namespace, but port names rupple up all the
		// way to the top-level namespace if necessary
		assertEquals("foo/a", a.getXnf());
		assertEquals("b", b.getXnf());
		assertEquals("foo/a", c.getXnf());
		assertEquals("foo/a", d.getXnf());
		assertEquals("foo/b/b", e.getXnf());
		assertEquals("foo/b/a/b", f.getXnf());
		// ports can also rename signals, ie. "g" is renamed to "a" here. "a" isn't a
		// port, so it doesn't get inherited from the parent namespace.
		assertEquals("foo/a", g.getXnf());
		assertEquals("foo/b/ar/a", h.getXnf());
		// port names can also be renamed
		// namespaces are simply hierarchical. in particular, foo/b/a *isn't* moved to
		// foo/b/a-1 because "a" is a port and thus is in the "foo" namespace, not in
		// "foo/b" (confusing as that may be)
		assertEquals("foo/b", bar1.getXnf());
		assertEquals("foo/b-a", bar2.getXnf());
		assertEquals("foo/b/a", bar3.getXnf());
		assertEquals("foo/b/ar", bar4.getXnf());
	}
}
