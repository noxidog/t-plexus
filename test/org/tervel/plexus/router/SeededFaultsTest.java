package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour the suite used to leave unpinned: each test is the one that fails when a documented rule is seeded
 * with a one-line fault.
 */
final class SeededFaultsTest {

    private static Plexus trained() {
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        p.fit(List.of(new int[]{1, 1, 0, 1, 0, 0, 1, 1, 1}), List.of(1));       // xx. / x.. / xxx
        return p;
    }

    /** Every 3×3 grid of the frame, as bits. */
    private static List<int[]> frame3() {
        final var out = new ArrayList<int[]>();
        for (var m = 0; m < 512; m++) {
            final var g = new int[9];
            for (var i = 0; i < 9; i++) g[i] = (m >> i) & 1;
            out.add(g);
        }
        return out;
    }

    private static List<int[]> randomGrids(Random rnd, int n) {
        final var out = new ArrayList<int[]>();
        for (var k = 0; k < n; k++) {
            final var g = new int[9];
            for (var i = 0; i < 9; i++) g[i] = rnd.nextInt(2);
            out.add(g);
        }
        return out;
    }

    // ---- the router's root and answers ------------------------------------------------------------

    @Test
    void theHexRootIsChosenForExactlyTheGridsItWasChosenForBefore() {
        final var p = new Plexus(3, 3, Set.of(Lattice.SQUARE_4, Lattice.HEX_6));
        var hex = 0;
        for (final var g : frame3()) if (p.describe(g).root() == Root.D6) hex++;
        assertEquals(101, hex, "the hex root wins a tie on symmetry exactly as documented");
    }

    @Test
    void anUnseenGridAnswersMinusOneAndScoresMinusOne() {
        final var p = trained();
        final var dot = new int[]{0, 0, 0, 0, 1, 0, 0, 0, 0};
        final var a = p.read(dot, Extrapolate.NONE);
        assertEquals(Verdict.UNSEEN, a.verdict());
        assertEquals(-1, a.label());
        assertEquals(-1, p.score(dot));
    }

    @Test
    void scoreReportsOneForAPositiveAndZeroForANegative() {
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        final var yes = new int[]{1, 1, 0, 1, 0, 0, 1, 1, 1};
        final var no = new int[]{0, 0, 0, 0, 1, 0, 0, 0, 0};
        p.fit(List.of(yes, no), List.of(1, 0));
        assertEquals(1, p.score(yes));
        assertEquals(0, p.score(no));
    }

    @Test
    void trainingKeepsItsOwnCopyOfTheCallersGrid() {
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        final var g = new int[]{1, 1, 0, 1, 0, 0, 1, 1, 1};
        p.accept(g, 1);
        g[0] = 0;                                        // the caller changes its array afterwards
        assertEquals(Verdict.POSITIVE, p.read(new int[]{1, 1, 0, 1, 0, 0, 1, 1, 1}, Extrapolate.NONE).verdict());
        // The stored example is what was trained, not the caller's array: a slice delivers the original grid.
        final var delivered = new java.util.ArrayList<int[]>();
        p.slice(Slice.on(Root.BOTH).label(1).extrapolate(Extrapolate.NONE), (x, c, e) -> delivered.add(x));
        assertEquals(1, delivered.size());
        assertEquals(java.util.List.of(1, 1, 0, 1, 0, 0, 1, 1, 1),
                java.util.Arrays.stream(delivered.get(0)).boxed().toList());
    }

    @Test
    void aSliceFilterOnJoinsKeepsBothEnds() {
        // The T has 5 joins (its adjacent pairs). A maximum of 4 must exclude it; a minimum of 6 must too.
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        p.fit(List.of(new int[]{1, 1, 0, 1, 0, 0, 1, 1, 1}), List.of(1));
        final var low = new java.util.ArrayList<int[]>();
        p.slice(Slice.on(Root.BOTH).label(1).joins(0, 4).extrapolate(Extrapolate.NONE), (x, c, e) -> low.add(x));
        assertEquals(0, low.size(), "a maximum of 4 joins excludes the 5-join T");
        final var inRange = new java.util.ArrayList<int[]>();
        p.slice(Slice.on(Root.BOTH).label(1).joins(5, 5).extrapolate(Extrapolate.NONE), (x, c, e) -> inRange.add(x));
        assertEquals(1, inRange.size(), "exactly 5 joins keeps it");
        final var high = new java.util.ArrayList<int[]>();
        p.slice(Slice.on(Root.BOTH).label(1).joins(6, 9).extrapolate(Extrapolate.NONE), (x, c, e) -> high.add(x));
        assertEquals(0, high.size(), "a minimum of 6 joins excludes it too");
    }

    @Test
    void aSliceFilterOnMassKeepsItsMinimum() {
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        p.fit(List.of(new int[]{1, 1, 0, 1, 0, 0, 1, 1, 1}), List.of(1));       // mass 6
        final var tooHeavy = new java.util.ArrayList<int[]>();
        p.slice(Slice.on(Root.BOTH).label(1).mass(7, 9).extrapolate(Extrapolate.NONE), (x, c, e) -> tooHeavy.add(x));
        assertEquals(0, tooHeavy.size(), "a minimum of 7 excludes the mass-6 T");
    }

    @Test
    void aNonZeroOneLabelOrCellIsRefused() {
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        assertThrows(IllegalArgumentException.class, () -> p.accept(new int[9], 2));
        assertThrows(IllegalArgumentException.class, () -> p.accept(new int[]{2, 0, 0, 0, 0, 0, 0, 0, 0}, 1));
    }

    @Test
    void describeReportsTheShapesBoxMassAndJoins() {
        final var d = trained().describe(new int[]{0, 0, 0, 0, 1, 0, 0, 0, 0});
        assertEquals(1, d.boxShort());
        assertEquals(1, d.boxLong());
        assertEquals(1, d.mass());
        assertEquals(0, d.joins());
    }

    @Test
    void renderShowsTheTrainedTrie() {
        final var empty = new Plexus(3, 3, Lattice.DEFAULT).render();
        final var rendered = trained().render();
        assertFalse(rendered.isBlank());
        assertNotEquals(empty, rendered);
    }

    // ---- slices -----------------------------------------------------------------------------------

    /** The slice with {@code q} delivers exactly the grids that read answers 1 and that satisfy {@code keep}. */
    private static void assertSliceIs(Plexus p, Slice q, Predicate<Descriptor> keep) {
        final var expected = new TreeSet<String>();
        for (final var g : frame3()) {
            final var a = p.read(g, q.extrapolate());
            if (a.label() == 1 && keep.test(p.describe(g))) expected.add(Arrays.toString(g));
        }
        final var delivered = new TreeSet<String>();
        p.slice(q, (g, c, e) -> delivered.add(Arrays.toString(g)));
        assertEquals(expected, delivered);
    }

    @Test
    void theMassAndJoinsBoundsFilterTheSliceAndTheGeneratedGrids() {
        final var rnd = new Random(11);
        for (var set = 0; set < 3; set++) {
            final var p = new Plexus(3, 3, Lattice.DEFAULT);
            p.fit(randomGrids(rnd, 6), List.of(1, 0, 1, 1, 0, 1));
            assertSliceIs(p, Slice.on(Root.BOTH).label(1).mass(2, 4).joins(1, 3).extrapolate(Extrapolate.EVERYTHING),
                    d -> d.mass() >= 2 && d.mass() <= 4 && d.joins() >= 1 && d.joins() <= 3);
        }
    }

    @Test
    void aTrainedGridOutsideTheMassRangeIsNotSliced() {
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        final var dot = new int[]{0, 0, 0, 0, 1, 0, 0, 0, 0};             // mass 1
        final var full = new int[9];
        Arrays.fill(full, 1);                                             // mass 9
        p.fit(List.of(dot, full), List.of(1, 1));
        final var delivered = new ArrayList<int[]>();
        p.slice(Slice.on(Root.BOTH).label(1).mass(2, 4), (g, c, e) -> delivered.add(g));
        for (final var g : delivered) {
            final var m = p.describe(g).mass();
            assertTrue(m >= 2 && m <= 4, "a grid of mass " + m + " is outside 2-4: " + Arrays.toString(g));
        }
    }

    @Test
    void aBoxConstraintKeepsOnlyThatBox() {
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        final var dot = new int[]{0, 0, 0, 0, 1, 0, 0, 0, 0};                  // a 1×1 box
        final var l = new int[]{1, 1, 0, 1, 0, 0, 1, 1, 1};                      // a 3×3 box
        p.fit(List.of(dot, l), List.of(1, 1));
        final var delivered = new ArrayList<int[]>();
        p.slice(Slice.on(Root.BOTH).box(3).label(1).extrapolate(Extrapolate.NONE), (g, c, e) -> delivered.add(g));
        assertFalse(delivered.isEmpty());
        for (final var g : delivered) {
            final var d = p.describe(g);
            assertTrue(d.boxShort() == 3 && d.boxLong() == 3, "box 3x3 only, got " + d.boxShort() + "x" + d.boxLong());
        }
    }

    @Test
    void anExactSymmetrySetFiltersTheSlice() {
        final var p = trained();
        assertSliceIs(p, Slice.on(Root.BOTH).label(1).exactly("rot180").extrapolate(Extrapolate.EVERYTHING),
                d -> new HashSet<>(d.symmetries()).equals(Set.of("rot180")));
    }

    @Test
    void assumedGridsComeOutDeepestFirst() {
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        p.fit(randomGrids(new Random(5), 6), List.of(1, 0, 1, 0, 1, 0));
        final var depths = new ArrayList<Integer>();
        final var sawAssumedBeforeConfirmed = new boolean[1];
        final var assumedSeen = new boolean[1];
        p.slice(Slice.on(Root.BOTH).label(1).extrapolate(Extrapolate.EVERYTHING), (g, c, e) -> {
            if (e == Slice.Evidence.ASSUMED) { assumedSeen[0] = true; depths.add(c.depth()); }
            else if (assumedSeen[0]) sawAssumedBeforeConfirmed[0] = true;
        });
        assertFalse(sawAssumedBeforeConfirmed[0], "trained grids come before assumed ones");
        for (var i = 1; i < depths.size(); i++)
            assertTrue(depths.get(i - 1) >= depths.get(i), "assumed grids run deepest first: " + depths);
    }

    @Test
    void anAssumedAnswerNextToDisagreementIsMarkedAsSuch() {
        var marked = 0;
        for (var set = 0; set < 10; set++) {
            final var p = new Plexus(3, 3, Lattice.DEFAULT);
            p.fit(randomGrids(new Random(20 + set), 6), List.of(1, 0, 1, 0, 1, 0));
            final var boundary = new ArrayList<Boolean>();
            p.slice(Slice.on(Root.BOTH).label(1).extrapolate(Extrapolate.EVERYTHING),
                    (g, c, e) -> { if (e == Slice.Evidence.ASSUMED) boundary.add(c.boundary()); });
            marked += (int) boundary.stream().filter(b -> b).count();
        }
        assertTrue(marked > 0, "some assumed answers stand beside disagreement, and are marked so");
    }

    @Test
    void aPerShapeEntryIsTheTrainedGridWhenThereIsOne() {
        final var p = trained();
        final var trained = new int[]{1, 1, 0, 1, 0, 0, 1, 1, 1};
        final var shape = p.describe(trained).canonical();
        final var evidence = new ArrayList<Slice.Evidence>();
        p.slice(Slice.on(Root.BOTH).label(1).per(Slice.Per.SHAPE).extrapolate(Extrapolate.EVERYTHING),
                (g, c, e) -> { if (p.describe(g).canonical().equals(shape)) evidence.add(e); });
        assertEquals(List.of(Slice.Evidence.CONFIRMED), evidence);
    }

    // ---- regression pins for the router ---------------------------------------------------------

    @Test
    void aSliceOnAnUntrainedRouterIsEmpty() {
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        final var delivered = new ArrayList<int[]>();
        p.slice(Slice.on(Root.BOTH).label(1).extrapolate(Extrapolate.EVERYTHING), (g, c, e) -> delivered.add(g));
        assertTrue(delivered.isEmpty());
    }
}
