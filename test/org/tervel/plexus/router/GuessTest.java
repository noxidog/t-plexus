package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The guess for an untrained grid: the nearest trained grid, its distance, and that the exact read is unchanged. */
final class GuessTest {

    private static int[] grid(String... rows) {
        final var g = new int[rows.length * rows[0].length()];
        for (var r = 0; r < rows.length; r++)
            for (var c = 0; c < rows[0].length(); c++) g[r * rows[0].length() + c] = rows[r].charAt(c) == 'x' ? 1 : 0;
        return g;
    }

    @Test
    void aCopyOfATrainedShapeAtAnotherPlacementIsAtDistanceZero() {
        final var p = new Plexus(4, 4);
        final var t = grid("xxx.", ".x..", ".x..", "....");
        p.accept(t, 1);
        final var moved = grid("....", ".xxx", "..x.", "..x.");                 // the same T, turned and moved
        final var g = p.guess(moved);
        assertEquals(0, g.distance());
        assertEquals(1, g.label());
        assertArrayEquals(t, g.nearest());
    }

    @Test
    void theDistanceCountsTheCellsOneShapeHasAndTheOtherDoesNot() {
        final var p = new Plexus(3, 3);
        p.accept(grid("xxx", ".x.", ".x."), 1);                                   // a T
        final var oneShort = grid("xxx", ".x.", "...");                          // the T with its stem shortened
        assertEquals(1, p.guess(oneShort).distance());
        assertEquals(1, p.guess(oneShort).label());
    }

    @Test
    void theNearestTrainedGridDecidesTheLabel() {
        final var p = new Plexus(3, 3);
        p.accept(grid("xxx", ".x.", ".x."), 1);                                   // a T, label 1
        p.accept(grid("x..", "x..", "xxx"), 0);                                   // an L, label 0
        assertEquals(1, p.guess(grid("xxx", ".x.", "..."  )).label());
        assertEquals(0, p.guess(grid("x..", "x..", "xx.")).label());
    }

    @Test
    void equidistantTrainedGridsWithDifferentLabelsGiveNoLabel() {
        final var p = new Plexus(3, 3);
        p.accept(grid("x..", "...", "..."), 1);                                   // one cell, label 1
        p.accept(grid("...", "...", "..x"), 0);                                   // one cell, label 0
        final var g = p.guess(grid("...", ".x.", "..."));                        // one cell, the same shape as both
        assertEquals(-1, g.label());
        assertEquals(0, g.distance());
    }

    @Test
    void withNothingTrainedThereIsNoGuess() {
        final var g = new Plexus(3, 3).guess(grid("x..", "...", "..."));
        assertEquals(-1, g.label());
        assertEquals(-1, g.distance());
        assertEquals(null, g.nearest());
    }

    @Test
    void theExactReadStillSaysUnseen() {
        final var p = new Plexus(3, 3, Lattice.DEFAULT);
        p.accept(grid("xxx", ".x.", ".x."), 1);
        assertEquals(Verdict.UNSEEN, p.read(grid("x..", "x..", "xx.")).verdict());
        assertEquals(1, p.guess(grid("x..", "x..", "xx.")).label(), "the only trained grid is the T, so its label");
    }
}
