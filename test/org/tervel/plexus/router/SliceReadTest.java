package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A slice produces exactly the grids that read answers with its label, under every extrapolation setting. */
final class SliceReadTest {

    @Test
    void aSliceIsExactlyWhatReadAnswers() {
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        p.fit(List.of(new int[]{1, 1, 0, 1, 0, 0, 1, 1, 1}), List.of(1));       // xx. / x.. / xxx
        assertSliceMatchesRead(p, 1);
    }

    @Test
    void aSliceIsExactlyWhatReadAnswersForRandomTrainingSets() {
        final var rnd = new Random(7);
        for (var set = 0; set < 10; set++) {
            final var grids = new java.util.ArrayList<int[]>();
            final var labels = new java.util.ArrayList<Integer>();
            for (var k = 0; k < 6; k++) {
                final var g = new int[9];
                for (var i = 0; i < 9; i++) g[i] = rnd.nextInt(2);
                grids.add(g);
                labels.add(rnd.nextInt(2));
            }
            final var p = new Plexus(3, 3, Lattice.DEFAULT);
            p.fit(grids, labels);
            assertSliceMatchesRead(p, 0);
            assertSliceMatchesRead(p, 1);
        }
    }

    @Test
    void aSliceOnTheShippedPathForkDatasetIsWhatReadingAnswers() {
        // The report's showcase: slices omitted grids that read answers. Both directions, on sampled grids: every
        // delivered grid reads with the label, and every sampled grid that reads with it is delivered.
        final var d = Fixtures.load("separable-path-fork-5x5");
        final var p = d.trained();
        final var rnd = new Random(31);
        for (final var e : Extrapolate.values())
            for (final var label : new int[]{0, 1}) {
                final var delivered = new java.util.TreeSet<String>();
                p.slice(Slice.on(Root.BOTH).label(label).extrapolate(e), (g, c, ev) -> {
                    delivered.add(Arrays.toString(g));
                    assertEquals(label, p.read(g, e).label(), "a delivered grid reads with its label, under " + e);
                });
                for (var k = 0; k < 20_000; k++) {
                    final var g = new int[25];
                    if (k % 2 == 0) {
                        for (var i = 0; i < 25; i++) g[i] = rnd.nextInt(2);
                    } else {
                        final var base = d.grids().get(rnd.nextInt(d.grids().size())).clone();
                        for (var f = 0; f < 1 + rnd.nextInt(3); f++) base[rnd.nextInt(25)] ^= 1;
                        System.arraycopy(base, 0, g, 0, 25);
                    }
                    final var a = p.read(g, e);
                    if (a.verdict() == Verdict.UNSEEN || a.label() != label) continue;
                    assertTrue(delivered.contains(Arrays.toString(g)), "read with label " + label + " under " + e + " but not delivered: "
                            + Arrays.toString(g));
                }
            }
    }

    @Test
    void aSliceOverAShapeThatFillsItsFrameFinishesPromptly() {
        // A 4x4 box (the four corners): the generator's worst case at this size. It took minutes before the pruning.
        final var p = new Plexus(4, 4);
        p.fit(List.of(new int[]{1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1}), List.of(1));
        assertTimeoutPreemptively(Duration.ofSeconds(20), () ->
                p.slice(Slice.on(Root.BOTH).label(1).extrapolate(Extrapolate.SYMMETRY), (g, c, e) -> { }));
    }

    /** The slice with {@code label} equals the grids of the 3×3 frame that read answers with that label, per setting. */
    private static void assertSliceMatchesRead(Plexus p, int label) {
        for (final var e : Extrapolate.values()) {
            final var read = new TreeSet<String>();
            for (var m = 0; m < 512; m++) {
                final var g = new int[9];
                for (var i = 0; i < 9; i++) g[i] = (m >> i) & 1;
                if (p.read(g, e).label() == label) read.add(Arrays.toString(g));
            }
            final var sliced = new TreeSet<String>();
            p.slice(Slice.on(Root.BOTH).label(label).extrapolate(e), (g, c, ev) -> sliced.add(Arrays.toString(g)));
            assertEquals(read, sliced, "label " + label + " under " + e);
        }
    }
}
