package org.tervel.plexus.descriptor;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Two descriptors are equal only when they describe the same grid in the same frame. */
final class DescriptorEqualityTest {

    private static int[] grid(String... rows) {
        final var g = new int[rows.length * rows[0].length()];
        for (var r = 0; r < rows.length; r++)
            for (var c = 0; c < rows[0].length(); c++) g[r * rows[0].length() + c] = rows[r].charAt(c) == 'x' ? 1 : 0;
        return g;
    }

    @Test
    void differentShapesWithTheSameKeyAreNotEqual() {
        // The L and the N are both five-cell paths in a 2x4 box: the same shape key, different shapes.
        final var l = Descriptor.of(grid("xxxx", "x...", "....", "...."), 4, 4);
        final var n = Descriptor.of(grid("xxx.", "..xx", "....", "...."), 4, 4);
        assertEquals(l.shapeKey(), n.shapeKey(), "the premise: the keys agree");
        assertNotEquals(l, n);
    }

    @Test
    void theSameGridInDifferentFramesIsNotEqual() {
        final var small = Descriptor.of(grid("xx", "x."), 2, 2);
        final var big = Descriptor.of(grid("xx.", "x..", "...", "..."), 4, 3);
        assertNotEquals(small, big);
    }

    @Test
    void theSameGridInTheSameFrameIsEqual() {
        assertEquals(Descriptor.of(grid("xx..", "x...", "....", "...."), 4, 4),
                Descriptor.of(grid("xx..", "x...", "....", "...."), 4, 4));
    }
}
