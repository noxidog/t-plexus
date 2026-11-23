package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** The learner's contradictions, duplicates, retraction and slice checks. */
final class Section2Test {

    private static final int[] T = {1, 1, 0, 1, 0, 0, 1, 1, 1};          // xx. / x.. / xxx

    @Test
    void equalEvidenceForBothLabelsAbstainsRatherThanAnsweringNegative() {
        final var p = new Plexus(3, 3);
        p.accept(T, 1);
        p.accept(T, 0);
        assertEquals(Verdict.UNSEEN, p.read(T).verdict(), "a 1:1 contradiction gives no verdict");
        assertEquals(-1, p.score(T));
    }

    @Test
    void aMajorityStillAnswers() {
        final var p = new Plexus(3, 3);
        p.accept(T, 1);
        p.accept(T, 1);
        p.accept(T, 0);
        assertEquals(Verdict.POSITIVE, p.read(T).verdict());
    }

    @Test
    void repeatedExamplesAddEvidenceWithoutGrowingTheTrie() {
        final var p = new Plexus(3, 3);
        p.accept(T, 1);
        final var nodes = p.size();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            for (var k = 0; k < 200_000; k++) p.accept(T, 1);
        });
        assertEquals(nodes, p.size(), "the same example adds no nodes");
        assertEquals(Verdict.POSITIVE, p.read(T).verdict());
    }

    @Test
    void retractingAContradictionRestoresTheAnswer() {
        final var p = new Plexus(3, 3);
        p.accept(T, 1);
        p.accept(T, 0);
        assertEquals(Verdict.UNSEEN, p.read(T).verdict());
        p.retract(T, 0);
        assertEquals(Verdict.POSITIVE, p.read(T).verdict());
        p.retract(T, 1);
        assertEquals(Verdict.UNSEEN, p.read(T).verdict(), "no evidence left, no answer");
    }

    @Test
    void retractingTheLastExampleLeavesTheRouterAsItWasBeforeTraining() {
        final var p = new Plexus(3, 3);
        final var before = p.size();
        p.accept(T, 1);
        p.retract(T, 1);
        assertEquals(before, p.size(), "the nodes the example created are dropped");
    }

    @Test
    void retractingAnExampleNeverTrainedIsRefused() {
        final var p = new Plexus(3, 3);
        p.accept(T, 1);
        assertThrows(IllegalArgumentException.class, () -> p.retract(T, 0));
        assertThrows(IllegalArgumentException.class, () -> p.retract(new int[9], 1));
    }

    @Test
    void aSliceFilterOutsideItsRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Slice.on(Root.BOTH).label(5));
        assertThrows(IllegalArgumentException.class, () -> Slice.on(Root.BOTH).mass(5, 2));
        assertThrows(IllegalArgumentException.class, () -> Slice.on(Root.BOTH).joins(3, 1));
    }

    @Test
    void anAnswerExplainsTheLevelItWasDecidedOnAndTheQueryValueThere() {
        final int[] tAt00 = {1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0};
        final int[] tAt11 = {0, 0, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1};
        final var p = new Plexus(4, 4);
        p.accept(tAt00, 1);
        final var a = p.read(tAt11, Extrapolate.PLACEMENT);
        assertEquals(Verdict.ASSUMED, a.verdict());
        assertEquals(Level.POSITION, a.context().splitsOn(), "the answer was decided at the corner");
        assertEquals(1 * 4096L + 1, a.context().queryValue(), "and the query's corner is row 1, column 1");
        assertEquals(null, p.read(tAt00, Extrapolate.NONE).context().splitsOn(), "a trained grid stops at a leaf");
    }

    @Test
    void aStandInIsNotStarvedByUnrelatedEvidence() {
        // T in a 4x4 frame, and T moved one cell down and right: the same shape at an unseen corner. Unrelated grids
        // elsewhere in the tree must not refuse the stand-in: it rests on T's own branch.
        final int[] tAt00 = {1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0};
        final int[] tAt11 = {0, 0, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1};
        final var p = new Plexus(4, 4);
        p.accept(tAt00, 1);
        assertEquals(Verdict.ASSUMED, p.read(tAt11, Extrapolate.PLACEMENT).verdict());
        final var rnd = new java.util.Random(2);
        for (var k = 0; k < 199; k++) {
            final var g = new int[16];
            final var length = 1 + rnd.nextInt(3);
            for (var i = 0; i < length; i++) g[i] = 1;                    // a row of 1 to 3 at the top
            p.accept(g, 0);
        }
        assertEquals(Verdict.ASSUMED, p.read(tAt11, Extrapolate.PLACEMENT).verdict());
    }

    @Test
    void aModelIsSavedAsItsExamplesAndRebuiltFromThemWithTheSameAnswers() {
        final var rnd = new java.util.Random(5);
        final var p = new Plexus(3, 3);
        final var grids = new ArrayList<int[]>();
        final var labels = new ArrayList<Integer>();
        for (var k = 0; k < 40; k++) {                                       // repeats included: they are evidence too
            final var g = new int[9];
            for (var i = 0; i < 9; i++) g[i] = rnd.nextInt(2);
            final var label = rnd.nextInt(2);
            grids.add(g);
            labels.add(label);
            p.accept(g, label);
        }
        final var saved = p.examples();
        assertEquals(40, saved.size(), "every training example comes back, once per training");
        final var before = new ArrayList<String>();
        for (var code = 0; code < 512; code++) before.add(answerOf(p, code));

        p.clear();
        assertEquals(new Plexus(3, 3).size(), p.size(), "clear leaves the model as it was constructed");
        final var grids2 = new ArrayList<int[]>();
        final var labels2 = new ArrayList<Integer>();
        for (final var e : saved) { grids2.add(e.grid()); labels2.add(e.label()); }
        p.fit(grids2, labels2);
        for (var code = 0; code < 512; code++) assertEquals(before.get(code), answerOf(p, code), "grid " + code);
    }

    private static String answerOf(Plexus p, int code) {
        final var g = new int[9];
        for (var i = 0; i < 9; i++) g[i] = (code >> i) & 1;
        final var a = p.read(g, Extrapolate.PLACEMENT);
        return a.verdict() + "/" + a.label();
    }

    @Test
    void aNoisyExampleStopsAnExactModelStandingInButNotATolerantOne() {
        final int[] tAt00 = {1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0};
        final int[] tAt11 = {0, 0, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1};
        final var exact = new Plexus(4, 4, Lattice.DEFAULT);
        final var tolerant = new Plexus(4, 4, Lattice.DEFAULT, 0.2);
        for (var k = 0; k < 9; k++) { exact.accept(tAt00, 1); tolerant.accept(tAt00, 1); }
        exact.accept(tAt00, 0);                                              // one mislabelled copy
        tolerant.accept(tAt00, 0);
        assertNotEquals(Verdict.ASSUMED, exact.read(tAt11, Extrapolate.PLACEMENT).verdict(),
                "exact purity: the contradicted branch stands in for nothing");
        assertEquals(Verdict.ASSUMED, tolerant.read(tAt11, Extrapolate.PLACEMENT).verdict(),
                "one label in ten is within the tolerance: the branch stands in");
        assertEquals(1, tolerant.read(tAt11, Extrapolate.PLACEMENT).label());
        assertEquals(0.2, tolerant.tolerance());
        assertThrows(IllegalArgumentException.class, () -> new Plexus(4, 4, Lattice.DEFAULT, 0.5));
    }

    @Test
    void fitAndAcceptInAnyOrderGiveTheSameAnswers() {
        final var rnd = new java.util.Random(11);
        for (var set = 0; set < 10; set++) {
            final var grids = new ArrayList<int[]>();
            final var labels = new ArrayList<Integer>();
            for (var k = 0; k < 20; k++) {
                final var g = new int[9];
                for (var i = 0; i < 9; i++) g[i] = rnd.nextInt(3) == 0 ? 1 : 0;
                grids.add(g);
                labels.add(rnd.nextInt(2));
            }
            final var byFit = new Plexus(3, 3);
            byFit.fit(grids, labels);
            final var byAccept = new Plexus(3, 3);
            for (var k = 0; k < grids.size(); k++) byAccept.accept(grids.get(k), labels.get(k));
            final var byReverse = new Plexus(3, 3);
            for (var k = grids.size() - 1; k >= 0; k--) byReverse.accept(grids.get(k), labels.get(k));
            for (var code = 0; code < 512; code++) {
                final var g = new int[9];
                for (var i = 0; i < 9; i++) g[i] = (code >>> i) & 1;
                for (final var ext : Extrapolate.values()) {
                    final var a = byFit.read(g, ext);
                    assertEquals(a.verdict(), byAccept.read(g, ext).verdict(), "set " + set + " grid " + code + " " + ext);
                    assertEquals(a.label(), byAccept.read(g, ext).label(), "set " + set + " grid " + code + " " + ext);
                    assertEquals(a.verdict(), byReverse.read(g, ext).verdict(), "reversed: set " + set + " grid " + code);
                    assertEquals(a.label(), byReverse.read(g, ext).label(), "reversed: set " + set + " grid " + code);
                }
            }
        }
    }

    @Test
    void aFrameWithANonPositiveOrOversizedSideIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Descriptor.of(new int[9], -3, -3));
        assertThrows(IllegalArgumentException.class, () -> Descriptor.of(new int[0], 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Plexus(4097, 1));
    }

    @Test
    void aTiedGridIsNotDeliveredBySlices() {
        final var p = new Plexus(3, 3);
        p.fit(List.of(T, T), List.of(1, 0));
        final var delivered = new java.util.ArrayList<int[]>();
        p.slice(Slice.on(Root.BOTH).extrapolate(Extrapolate.NONE), (g, c, e) -> delivered.add(g));
        assertEquals(0, delivered.size(), "a contradicted grid is delivered neither as 1 nor as 0");
    }
}
