package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** On a small frame, a slice lists exactly the grids a read answers with that label, once each. */
final class SliceCompletenessTest {

    @Test
    void aSliceOnA4x4FrameListsExactlyWhatReadingAnswers() {
        final var rnd = new Random(3);
        final var p = new Plexus(4, 4);
        for (var k = 0; k < 40; k++) {
            final var g = new int[16];
            for (var i = 0; i < 16; i++) g[i] = rnd.nextInt(3) == 0 ? 1 : 0;
            p.accept(g, rnd.nextInt(2));
        }
        for (final var setting : List.of(Extrapolate.PLACEMENT, Extrapolate.EVERYTHING))
            for (final var label : List.of(0, 1)) {
                final var sliced = new ArrayList<String>();
                p.slice(Slice.on(Root.BOTH).extrapolate(setting).label(label), (g, c, e) -> sliced.add(Arrays.toString(g)));
                final Set<String> read = new HashSet<>();
                for (var code = 0; code < 1 << 16; code++) {
                    final var g = new int[16];
                    for (var i = 0; i < 16; i++) g[i] = (code >>> i) & 1;
                    final var a = p.read(g, setting);
                    if (a.verdict() != Verdict.UNSEEN && a.label() == label) read.add(Arrays.toString(g));
                }
                assertEquals(sliced.size(), new HashSet<>(sliced).size(), setting + " label " + label + ": no grid twice");
                assertEquals(read, new HashSet<>(sliced), setting + " label " + label + ": the slice is the read's answers");
            }
    }
}
