package org.tervel.plexus.router;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A slice's constraints hold for trained grids, a box of the largest side packs, and size() is safe beside training. */
final class ReviewFixesTest {

    private static final int[] BLOCK = {1, 1, 0, 1, 1, 0, 0, 0, 0};
    private static final int[] EMPTY = new int[9];

    private static List<int[]> confirmed(Plexus p, Slice q) {
        final var out = new ArrayList<int[]>();
        p.slice(q, (grid, ctx, evidence) -> {
            if (evidence == Slice.Evidence.CONFIRMED) out.add(grid);
        });
        return out;
    }

    @Test
    void aSliceFiltersTrainedGridsByEveryConstraint() {
        // Two trained grids whose paths split on nothing below the box: their edges test no orientation, joins or
        // corner, so only the description of each can.
        final var p = new Plexus(3, 3);
        p.accept(BLOCK, 1);
        p.accept(EMPTY, 1);
        assertEquals(2, confirmed(p, Slice.on(Root.D4).extrapolate(Extrapolate.NONE)).size());
        assertTrue(confirmed(p, Slice.on(Root.D4).extrapolate(Extrapolate.NONE).orientation(0)).isEmpty(),
                "both have orientation -1: neither is a grid of orientation 0");
        assertTrue(confirmed(p, Slice.on(Root.D4).extrapolate(Extrapolate.NONE).orientation(1)).isEmpty());
        assertTrue(confirmed(p, Slice.on(Root.D4).extrapolate(Extrapolate.NONE).joins(1, 3)).isEmpty(),
                "the block has 4 joins and the empty grid 0");
        final var corner = confirmed(p, Slice.on(Root.D4).extrapolate(Extrapolate.NONE).corner(1, 1));
        assertTrue(corner.isEmpty(), "the block sits at (0,0), and so does the empty grid");
        final var block = confirmed(p, Slice.on(Root.D4).extrapolate(Extrapolate.NONE).joins(4, 4));
        assertEquals(1, block.size());
        assertEquals(List.of(1, 1, 0, 1, 1, 0, 0, 0, 0), java.util.Arrays.stream(block.get(0)).boxed().toList());
    }

    @Test
    void aBoxOfTheLargestSidePacksAndUnpacks() {
        final var p = new Plexus(1, Plexus.MAX_SIDE);
        final var row = new int[Plexus.MAX_SIDE];
        java.util.Arrays.fill(row, 1);
        p.accept(row, 1);
        assertTrue(p.render().contains("box=1x" + Plexus.MAX_SIDE), p.render());
        final var found = confirmed(p, Slice.on(Root.D4).extrapolate(Extrapolate.NONE).box(1, Plexus.MAX_SIDE));
        assertEquals(1, found.size(), "the full row is found by its own box");
    }

    @Test
    void sizeIsSafeBesideTraining() throws Exception {
        final var p = new Plexus(4, 4);
        final var failure = new AtomicReference<Throwable>();
        final var trainer = new Thread(() -> {
            for (var m = 0; m < 65536; m++) {
                final var g = new int[16];
                for (var i = 0; i < 16; i++) g[i] = (m >> i) & 1;
                p.accept(g, Integer.bitCount(m) & 1);
            }
        });
        trainer.start();
        var last = 0;
        while (trainer.isAlive()) {
            try {
                final var n = p.size();
                assertTrue(n >= last, "the node count never falls while training only adds: " + last + " then " + n);
                last = n;
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
                break;
            }
        }
        trainer.join();
        assertNull(failure.get());
        assertEquals(p.size(), p.size());
    }
}
