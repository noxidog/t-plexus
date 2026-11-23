package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.util.List;

/** Probe, not a test: the report's L-shaped 4x4-box grid in a 6x6 frame, default slice. */
final class ProbeSliceL6 {
    public static void main(String[] args) {
        final var p = new Plexus(6, 6);
        final var g = new int[36];
        for (var c = 0; c < 4; c++) g[c] = 1;                           // xxxx
        for (var r = 1; r < 4; r++) g[r * 6] = 1;                       // x... below it
        p.fit(List.of(g), List.of(1));
        final var t0 = System.nanoTime();
        final var n = new int[1];
        final var result = p.slice(Slice.on(Root.BOTH).label(1), (x, c, e) -> n[0]++);
        System.out.println("6x6, L 4x4 box, SYMMETRY: " + (System.nanoTime() - t0) / 1_000_000 + " ms, " + n[0]
                + " grids, " + result.skipped() + " skipped");
    }
}
