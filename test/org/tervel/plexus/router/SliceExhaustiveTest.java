package org.tervel.plexus.router;

import org.junit.jupiter.api.Test;

import org.tervel.plexus.descriptor.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** On a frame above 16 cells, the exhaustive switch makes a slice list exactly what reading answers. */
final class SliceExhaustiveTest {

    @Test
    void exhaustiveSliceOn4x5ListsExactlyWhatReadingAnswers() {
        final var p = new Plexus(4, 5);
        p.accept(new int[]{1, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0}, 1);
        p.accept(new int[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0}, 0);
        final var listed = new HashSet<String>();
        final var result = p.slice(Slice.on(Root.BOTH).extrapolate(Extrapolate.PLACEMENT).label(1).exhaustive(true),
                (g, c, e) -> listed.add(Arrays.toString(g)));
        assertEquals(0, result.skipped());
        final Set<String> read = new HashSet<>();
        for (long code = 0; code < 1L << 20; code++) {
            final var g = new int[20];
            for (var i = 0; i < 20; i++) g[i] = (int) ((code >>> i) & 1);
            final var a = p.read(g, Extrapolate.PLACEMENT);
            if (a.verdict() != Verdict.UNSEEN && a.label() == 1) read.add(Arrays.toString(g));
        }
        assertEquals(read, listed, "the exhaustive slice is the read's answers");
    }

    @Test
    void aFrameAboveThirtyCellsIsRefusedEvenExhaustively() {
        final var p = new Plexus(6, 6);
        assertThrows(IllegalArgumentException.class,
                () -> p.slice(Slice.on(Root.BOTH).label(1).exhaustive(true), (g, c, e) -> { }));
    }
}
