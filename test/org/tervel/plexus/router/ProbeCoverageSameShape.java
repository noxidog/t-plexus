package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Probe, not a test: on 4x4 grids with a quarter of the placements trained, how many held-out grids each setting
 * answers and how many of those answers are right: placements of trained shapes, and grids of shapes never trained.
 * Two labels: one that depends on the shape only (mass at least 8), and one drawn at random per grid.
 */
final class ProbeCoverageSameShape {
    public static void main(String[] args) {
        final var all = new ArrayList<int[]>();
        for (var m = 0; m < 65536; m++) {
            final var g = new int[16];
            for (var i = 0; i < 16; i++) g[i] = (m >> i) & 1;
            all.add(g);
        }
        final var coin = new Random(7);
        final var random = new int[65536];
        for (var m = 0; m < 65536; m++) random[m] = coin.nextInt(2);
        run("mass >= 8", all, m -> Integer.bitCount(m) >= 8 ? 1 : 0);
        run("random", all, m -> random[m]);
    }

    private static void run(String name, List<int[]> all, IntUnaryOperator label) {
        final var rnd = new Random(43);
        final var p = new Plexus(4, 4);
        final Set<Integer> trainedIdx = new HashSet<>();
        final Set<String> trainedShapes = new HashSet<>();
        final var grids = new ArrayList<int[]>();
        final var labels = new ArrayList<Integer>();
        for (var m = 1; m < 65536; m++) {
            if (rnd.nextDouble() >= 0.25) continue;                 // a quarter of the placements are trained
            final var d = p.describe(all.get(m));
            trainedIdx.add(m);
            trainedShapes.add(d.root() + "|" + d.canonical());
            grids.add(all.get(m));
            labels.add(label.applyAsInt(m));
        }
        p.fit(grids, labels);
        System.out.println("label " + name + ": " + grids.size() + " grids trained");
        for (final var e : Extrapolate.values()) {
            int same = 0, sameAnswered = 0, sameRight = 0, other = 0, otherAnswered = 0, otherRight = 0;
            for (var m = 1; m < 65536; m++) {
                if (trainedIdx.contains(m)) continue;
                final var d = p.describe(all.get(m));
                final var a = p.read(all.get(m), e);
                final var answered = a.verdict() != Verdict.UNSEEN;
                final var right = answered && a.label() == label.applyAsInt(m);
                if (trainedShapes.contains(d.root() + "|" + d.canonical())) {
                    same++;
                    if (answered) sameAnswered++;
                    if (right) sameRight++;
                } else {
                    other++;
                    if (answered) otherAnswered++;
                    if (right) otherRight++;
                }
            }
            System.out.printf("  %-10s placements of trained shapes: %d of %d answered, %d right;"
                    + " grids of untrained shapes: %d of %d answered, %d right%n",
                    e, sameAnswered, same, sameRight, otherAnswered, other, otherRight);
        }
    }
}
