package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Probe, not a test: does fit() give the same router as accept() in the same order? And how fast is accept()? */
final class ProbeFitAccept {
    public static void main(String[] args) {
        final var rnd = new Random(9);
        var disagree = 0;
        var comparisons = 0;
        for (var set = 0; set < 100; set++) {
            final var grids = new ArrayList<int[]>();
            final var labels = new ArrayList<Integer>();
            for (var k = 0; k < 8; k++) {
                final var g = new int[9];
                for (var i = 0; i < 9; i++) g[i] = rnd.nextInt(2);
                grids.add(g);
                labels.add(rnd.nextInt(2));
            }
            final var f = new Plexus(3, 3);
            f.fit(grids, labels);
            final var a = new Plexus(3, 3);
            for (var k = 0; k < grids.size(); k++) a.accept(grids.get(k), labels.get(k));
            for (var m = 0; m < 512; m++) {
                final var g = new int[9];
                for (var i = 0; i < 9; i++) g[i] = (m >> i) & 1;
                for (final var e : Extrapolate.values()) {
                    comparisons++;
                    if (f.read(g, e).label() != a.read(g, e).label() || f.read(g, e).verdict() != a.read(g, e).verdict())
                        disagree++;
                }
            }
        }
        System.out.println("fit vs accept: " + disagree + " of " + comparisons + " answers differ");

        final var big = new Plexus(4, 4);
        final var t0 = System.nanoTime();
        for (var k = 0; k < 8000; k++) {
            final var g = new int[16];
            for (var i = 0; i < 16; i++) g[i] = rnd.nextInt(2);
            big.accept(g, rnd.nextInt(2));
        }
        System.out.println("8000 accepts in 4x4: " + (System.nanoTime() - t0) / 1_000_000 + " ms");
    }
}
