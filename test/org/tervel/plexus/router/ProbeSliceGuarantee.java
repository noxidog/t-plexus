package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/**
 * Probe, not a test: the slice guarantee on the shipped separable path/fork dataset, checked in both directions on
 * samples. Every slice output must read back with the label; every sampled grid read with the label must be delivered.
 */
final class ProbeSliceGuarantee {

    public static void main(String[] args) throws Exception {
        final var lines = Files.readAllLines(Path.of("data", "separable-path-fork-5x5.txt"));
        final var grids = new ArrayList<int[]>();
        final var labels = new ArrayList<Integer>();
        for (var i = 1; i + 5 < lines.size(); i += 6) {
            final var g = new int[25];
            for (var r = 0; r < 5; r++)
                for (var c = 0; c < 5; c++) g[r * 5 + c] = lines.get(i + r).charAt(c) == 'x' ? 1 : 0;
            grids.add(g);
            labels.add(Integer.parseInt(lines.get(i + 5).trim()));
        }
        final var p = new Plexus(5, 5);
        p.fit(grids, labels);
        final var rnd = new Random(31);
        for (final var e : Extrapolate.values()) {
            for (final var label : new int[]{0, 1}) {
                final var sliced = new TreeSet<String>();
                final var delivered = new ArrayList<int[]>();
                p.slice(Slice.on(Root.BOTH).label(label).extrapolate(e), (g, c, ev) -> { delivered.add(g); sliced.add(Arrays.toString(g)); });
                var notRead = 0;
                for (final var g : delivered) if (p.read(g, e).label() != label) notRead++;
                var missed = 0;
                var sampled = 0;
                for (var k = 0; k < 300_000; k++) {
                    final var g = new int[25];
                    if (k % 2 == 0) {
                        for (var i = 0; i < 25; i++) g[i] = rnd.nextInt(2);
                    } else {
                        final var base = grids.get(rnd.nextInt(grids.size())).clone();
                        for (var f = 0; f < 1 + rnd.nextInt(3); f++) base[rnd.nextInt(25)] ^= 1;
                        System.arraycopy(base, 0, g, 0, 25);
                    }
                    final var a = p.read(g, e);
                    if (a.verdict() == Verdict.UNSEEN || a.label() != label) continue;
                    sampled++;
                    if (!sliced.contains(Arrays.toString(g))) missed++;
                }
                System.out.println(e + " label " + label + ": slice " + delivered.size() + ", delivered grids read otherwise " + notRead
                        + ", sampled read-with-label " + sampled + ", missing from slice " + missed);
            }
        }
    }
}
