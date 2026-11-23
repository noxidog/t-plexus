package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/** Probe, not a test: on 4x4 random training sets, does the slice deliver exactly the grids read answers with the label? */
final class ProbeSliceVsRead4 {
    public static void main(String[] args) {
        final var rnd = new Random(12);
        var mismatches = 0;
        for (var set = 0; set < 6; set++) {
            final var grids = new ArrayList<int[]>();
            final var labels = new ArrayList<Integer>();
            for (var k = 0; k < 6; k++) {
                final var g = new int[16];
                final var p = rnd.nextDouble();
                for (var i = 0; i < 16; i++) g[i] = rnd.nextDouble() < p ? 1 : 0;
                grids.add(g);
                labels.add(rnd.nextInt(2));
            }
            final var p = new Plexus(4, 4);
            p.fit(grids, labels);
            for (final var e : Extrapolate.values()) {
                for (final var label : new int[]{0, 1}) {
                    final var read = new TreeSet<String>();
                    for (var m = 0; m < 65536; m++) {
                        final var g = new int[16];
                        for (var i = 0; i < 16; i++) g[i] = (m >> i) & 1;
                        if (p.read(g, e).label() == label) read.add(Arrays.toString(g));
                    }
                    final var sliced = new TreeSet<String>();
                    p.slice(Slice.on(Root.BOTH).label(label).extrapolate(e), (g, c, ev) -> sliced.add(Arrays.toString(g)));
                    if (!read.equals(sliced)) {
                        mismatches++;
                        System.out.println("set " + set + " " + e + " label " + label + ": read " + read.size() + " sliced " + sliced.size());
                    }
                }
            }
        }
        System.out.println("mismatches: " + mismatches);
    }
}
