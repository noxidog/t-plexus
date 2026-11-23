package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Probe, not a test: coverage of held-out placements of trained shapes, and of untrained shapes, on 4x4 grids. Labels
 * depend on the shape only (mass at least 8), so every placement of a shape should be answerable once one is trained.
 */
final class ProbeCoverageSameShape {
    public static void main(String[] args) {
        final var rnd = new Random(43);
        final var p = new Plexus(4, 4);
        final Map<String, Integer> classOf = new HashMap<>();
        final var all = new ArrayList<int[]>();
        for (var m = 0; m < 65536; m++) {
            final var g = new int[16];
            for (var i = 0; i < 16; i++) g[i] = (m >> i) & 1;
            all.add(g);
        }
        final Set<Integer> trainedIdx = new HashSet<>();
        final Set<String> trainedShapes = new HashSet<>();
        final var grids = new ArrayList<int[]>();
        final var labels = new ArrayList<Integer>();
        for (var m = 1; m < 65536; m++) {
            final var d = p.describe(all.get(m));
            final var shape = d.root() + "|" + d.canonical();
            if (rnd.nextDouble() < 0.25) {                  // a quarter of the placements are trained
                trainedIdx.add(m);
                trainedShapes.add(shape);
                grids.add(all.get(m));
                labels.add(d.mass() >= 8 ? 1 : 0);
            }
        }
        p.fit(grids, labels);
        var sameShape = 0;
        var sameShapeAnswered = 0;
        var untrainedShape = 0;
        var untrainedShapeAnswered = 0;
        for (var m = 1; m < 65536; m++) {
            if (trainedIdx.contains(m)) continue;
            final var d = p.describe(all.get(m));
            final var shape = d.root() + "|" + d.canonical();
            final var answered = p.read(all.get(m), Extrapolate.PLACEMENT).verdict() != Verdict.UNSEEN;
            if (trainedShapes.contains(shape)) {
                sameShape++;
                if (answered) sameShapeAnswered++;
            } else {
                untrainedShape++;
                if (answered) untrainedShapeAnswered++;
            }
        }
        System.out.printf("held-out placements of trained shapes: %d of %d answered (%.1f%%)%n",
                sameShapeAnswered, sameShape, 100.0 * sameShapeAnswered / sameShape);
        System.out.printf("held-out grids of untrained shapes: %d of %d answered (%.1f%%)%n",
                untrainedShapeAnswered, untrainedShape, 100.0 * untrainedShapeAnswered / untrainedShape);
    }
}
