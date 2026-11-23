package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.util.List;

/** Probe, not a test: how long does one default-setting slice take, and how many grids does it deliver? */
final class ProbeSliceCost {
    public static void main(String[] args) {
        final var p = new Plexus(4, 4);
        p.fit(List.of(new int[]{1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1}), List.of(1));   // a 4x4 box: corners
        final var t0 = System.nanoTime();
        final var n = new int[1];
        final var result = p.slice(Slice.on(Root.BOTH).label(1), (g, c, e) -> n[0]++);
        System.out.println("4x4 one grid, SYMMETRY: " + (System.nanoTime() - t0) / 1_000_000 + " ms, "
                + n[0] + " grids, " + result.skipped() + " skipped");
    }
}
