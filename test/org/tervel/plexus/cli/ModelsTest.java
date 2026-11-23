package org.tervel.plexus.cli;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tervel.plexus.router.Extrapolate;
import org.tervel.plexus.descriptor.Lattice;
import org.tervel.plexus.router.Plexus;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A saved router loads back to the same answers, on every grid of its frame. */
final class ModelsTest {

    @TempDir
    Path dir;

    @Test
    void aSavedRouterLoadsBackWithTheSameAnswersOnEveryGrid() throws Exception {
        final var rnd = new Random(7);
        final var router = new Plexus(3, 3, Lattice.DEFAULT);
        for (var k = 0; k < 30; k++) {
            final var g = new int[9];
            for (var i = 0; i < 9; i++) g[i] = rnd.nextInt(2);
            router.accept(g, rnd.nextInt(2));
        }
        final var file = dir.resolve("model.txt");
        Models.save(router, file);
        final var loaded = Models.load(file, Lattice.DEFAULT, 0);
        assertEquals(router.examples().size(), loaded.examples().size(), "every training example is saved");
        for (var code = 0; code < 512; code++) {
            final var g = new int[9];
            for (var i = 0; i < 9; i++) g[i] = (code >>> i) & 1;
            final var a = router.read(g, Extrapolate.PLACEMENT);
            final var b = loaded.read(g, Extrapolate.PLACEMENT);
            assertEquals(a.verdict(), b.verdict(), "grid " + code);
            assertEquals(a.label(), b.label(), "grid " + code);
        }
    }
}
