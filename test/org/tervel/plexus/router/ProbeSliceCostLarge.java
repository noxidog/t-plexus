package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.util.List;

/** Probe, not a test: a slice where the box has about twenty independent cells. */
final class ProbeSliceCostLarge {
    public static void main(String[] args) {
        final var p = new Plexus(5, 5);
        // An asymmetric 4x5 box: a staircase with no symmetry.
        final var g = new int[25];
        final int[][] cells = {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 0}, {1, 1}, {2, 0}, {3, 0}, {3, 4}, {2, 3}};
        for (final var c : cells) g[c[0] * 5 + c[1]] = 1;
        p.fit(List.of(g), List.of(1));
        final var t0 = System.nanoTime();
        final var n = new int[1];
        final var result = p.slice(Slice.on(Root.BOTH).label(1), (x, c, e) -> n[0]++);
        System.out.println("5x5 asymmetric shape, SYMMETRY: " + (System.nanoTime() - t0) / 1_000_000 + " ms, "
                + n[0] + " grids, " + result.skipped());
    }
}
