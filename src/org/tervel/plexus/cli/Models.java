package org.tervel.plexus.cli;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import org.tervel.plexus.descriptor.Lattice;
import org.tervel.plexus.router.Plexus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Saving and loading a trained router as a data file: the same format the interpreter reads, with every training
 * example written out once per training. The file holds the frame and the examples only. The lattices and the noise
 * tolerance are not in it: the caller gives them to {@link #load}, and they must match the router that was saved.
 */
public final class Models {

    private Models() { }

    /** Writes {@code router}'s examples to {@code file}, in the interpreter's data-file format. */
    public static void save(Plexus router, Path file) throws IOException {
        final var rows = router.inRows();
        final var cols = router.inCols();
        final var lines = new ArrayList<String>();
        lines.add(rows + "x" + cols);
        for (final var e : router.examples()) {
            for (var r = 0; r < rows; r++) {
                final var row = new StringBuilder();
                for (var c = 0; c < cols; c++) row.append(e.grid()[r * cols + c] == 1 ? 'x' : '_');
                lines.add(row.toString());
            }
            lines.add(Integer.toString(e.label()));
        }
        Files.write(file, lines);
    }

    /**
     * A router on {@code lattices} and {@code tolerance}, trained on the examples in {@code file}. The file must have
     * been written by {@link #save}, or be in the same format: a header {@code RxC}, then records of rows and a label.
     *
     * @throws IOException if the file cannot be read
     * @throws IllegalArgumentException if the file is malformed, or the lattices or tolerance are not legal
     */
    public static Plexus load(Path file, Set<Lattice> lattices, double tolerance) throws IOException {
        final var dims = Main.dimensions(file);
        final var data = Main.parse(file);
        final var router = new Plexus(dims[0], dims[1], lattices, tolerance);
        final List<int[]> grids = new ArrayList<>();
        final List<Integer> labels = new ArrayList<>();
        for (final var e : data) {
            grids.add(e.grid());
            labels.add(e.label());
        }
        router.fit(grids, labels);
        return router;
    }
}
