package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Probe, not a test: heap used per stored example, on 6x6 random grids with random labels. */
final class ProbeMemory {
    public static void main(String[] args) {
        final var rnd = new Random(51);
        final var grids = new ArrayList<int[]>();
        final var labels = new ArrayList<Integer>();
        for (var k = 0; k < 20_000; k++) {
            final var g = new int[36];
            for (var i = 0; i < 36; i++) g[i] = rnd.nextInt(2);
            grids.add(g);
            labels.add(rnd.nextInt(2));
        }
        final var rt = Runtime.getRuntime();
        System.gc();
        final var before = rt.totalMemory() - rt.freeMemory();
        final var p = new Plexus(6, 6);
        p.fit(grids, labels);
        System.gc();
        final var after = rt.totalMemory() - rt.freeMemory();
        System.out.println("bytes per example: " + (after - before) / grids.size() + "  (" + p.size() + " nodes)");
    }
}
