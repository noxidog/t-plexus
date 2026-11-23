package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Probe, not a test: the share of untrained 4x4 grids each setting answers, for several training fractions. */
final class ProbeCoverage {
    public static void main(String[] args) {
        final var rnd = new Random(41);
        for (final var fraction : new double[]{0.02, 0.10, 0.50}) {
            final var trainIdx = new ArrayList<Integer>();
            final var labels = new ArrayList<Integer>();
            final var grids = new ArrayList<int[]>();
            final var trained = new java.util.HashSet<Integer>();
            for (var m = 0; m < 65536; m++) {
                if (rnd.nextDouble() >= fraction) continue;
                trained.add(m);
                final var g = new int[16];
                for (var i = 0; i < 16; i++) g[i] = (m >> i) & 1;
                grids.add(g);
                labels.add(Integer.bitCount(m) >= 8 ? 1 : 0);
            }
            final var p = new Plexus(4, 4);
            p.fit(grids, labels);
            final var line = new StringBuilder(String.format("trained %.0f%% (%d grids):", fraction * 100, grids.size()));
            for (final var e : Extrapolate.values()) {
                var answered = 0;
                var held = 0;
                for (var m = 0; m < 65536; m++) {
                    if (trained.contains(m)) continue;
                    held++;
                    final var g = new int[16];
                    for (var i = 0; i < 16; i++) g[i] = (m >> i) & 1;
                    if (p.read(g, e).verdict() != Verdict.UNSEEN) answered++;
                }
                line.append(String.format("  %s %.1f%%", e, 100.0 * answered / held));
            }
            System.out.println(line);
        }
    }
}
