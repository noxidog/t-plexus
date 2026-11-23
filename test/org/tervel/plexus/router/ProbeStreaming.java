package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.util.Random;

/** Probe, not a test: accept() loops on random 6x6 labels, and repeated duplicates. */
final class ProbeStreaming {
    public static void main(String[] args) {
        final var rnd = new Random(5);
        for (final var n : new int[]{2000, 4000, 8000}) {
            final var grids = new java.util.ArrayList<int[]>();
            final var labels = new java.util.ArrayList<Integer>();
            for (var k = 0; k < n; k++) {
                final var g = new int[36];
                for (var i = 0; i < 36; i++) g[i] = rnd.nextInt(2);
                grids.add(g);
                labels.add(rnd.nextInt(2));
            }
            final var a = new Plexus(6, 6);
            final var t0 = System.nanoTime();
            for (var k = 0; k < n; k++) a.accept(grids.get(k), labels.get(k));
            System.out.println("6x6 accept loop, " + n + " random examples: " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        }
        final var dup = new Plexus(3, 3);
        final int[] one = {1, 1, 0, 1, 0, 0, 1, 1, 1};
        final var t0 = System.nanoTime();
        for (var k = 0; k < 200_000; k++) dup.accept(one, 1);
        final var r0 = System.nanoTime();
        final var v = dup.read(one).verdict();
        System.out.println("200000 duplicates: " + (r0 - t0) / 1_000_000 + " ms to train, read " + (System.nanoTime() - r0) / 1000 + " us, " + v);
    }
}
