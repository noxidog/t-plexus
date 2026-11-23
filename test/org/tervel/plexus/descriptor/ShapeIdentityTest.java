package org.tervel.plexus.descriptor;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A read answers for the shape it is given: two shapes that share a leaf do not lend each other labels. */
final class ShapeIdentityTest {

    private static int[] grid(String... rows) {
        final var g = new int[rows.length * rows[0].length()];
        for (var r = 0; r < rows.length; r++)
            for (var c = 0; c < rows[0].length(); c++) g[r * rows[0].length() + c] = rows[r].charAt(c) == 'x' ? 1 : 0;
        return g;
    }

    @Test
    void aMirrorImageNeverBorrowsTheLabelOfTheShapeItLandsNextTo() {
        final var p = new Plexus(4, 4, Lattice.DEFAULT);
        p.fit(List.of(grid("x.x.", "x...", "....", "...."), grid(".xx.", "x...", "....", "....")), List.of(1, 0));
        // The mirror image of the label-0 shape: the same shape class, but at a pose the label-1 shape occupies.
        final var a = p.read(grid(".xx.", "...x", "....", "...."), Extrapolate.PLACEMENT);
        assertTrue(a.verdict() == Verdict.UNSEEN || a.label() == 0, "borrowed label " + a.label());
    }

    @Test
    void aDifferentShapeOnTheSameLeafIsNotAnswered() {
        final var p = new Plexus(4, 4, Lattice.DEFAULT);
        final var a = grid("xx..", "x...", "....", "....");                  // an L
        final var b = grid("xx..", ".x..", "....", "....");                  // a different L
        p.fit(List.of(a), List.of(1));
        final var verdict = p.read(b, Extrapolate.NONE).verdict();
        assertEquals(Verdict.UNSEEN, verdict, "an untrained shape is not answered from another shape's leaf");
    }

    /** A 5x5 frame with {@code cells} placed at (row, col): 'x' filled. */
    private static int[] placed(int row, int col, String... cells) {
        final var g = new int[25];
        for (var r = 0; r < cells.length; r++)
            for (var c = 0; c < cells[r].length(); c++)
                if (cells[r].charAt(c) == 'x') g[(row + r) * 5 + col + c] = 1;
        return g;
    }

    @Test
    void twoPentominoesThatAreOnePathBentDifferentlyAreTold() {
        // The L and the N are both five-cell paths: no adjacency primitive separates them.
        final var p = new Plexus(5, 5, Lattice.DEFAULT);
        p.accept(placed(0, 0, "xxxx", "x..."), 1);                  // L at (0,0)
        p.accept(placed(2, 1, "xxx.", "..xx"), 0);                  // N at (2,1)
        assertEquals(1, p.read(placed(2, 1, "xxxx", "x..."), Extrapolate.PLACEMENT).label(), "the L at the N's corner");
        assertEquals(0, p.read(placed(0, 0, "xxx.", "..xx"), Extrapolate.PLACEMENT).label(), "the N at the L's corner");
    }

    @Test
    void underNoneAnAnswerForAnUntrainedGridComesFromItsOwnShape() {
        final var rnd = new Random(31);
        final var grids = new ArrayList<int[]>();
        final var labels = new ArrayList<Integer>();
        for (var k = 0; k < 40; k++) {
            final var g = new int[16];
            for (var i = 0; i < 16; i++) g[i] = rnd.nextInt(2);
            grids.add(g);
            labels.add(rnd.nextInt(2));
        }
        final var p = new Plexus(4, 4, Lattice.DEFAULT);
        p.fit(grids, labels);
        var answered = 0;
        for (var k = 0; k < 2000; k++) {
            final var g = new int[16];
            for (var i = 0; i < 16; i++) g[i] = rnd.nextInt(2);
            final var a = p.read(g, Extrapolate.NONE);
            if (a.verdict() == Verdict.UNSEEN) continue;
            answered++;
            final var d = p.describe(g);
            var sameShapeLabels = new ArrayList<Integer>();
            for (var i = 0; i < grids.size(); i++)
                if (p.describe(grids.get(i)).canonical().equals(d.canonical())) sameShapeLabels.add(labels.get(i));
            assertTrue(sameShapeLabels.contains(a.label()),
                    "answered " + a.label() + " for a shape never trained with that label");
        }
        assertTrue(answered > 0, "some untrained grids are answered, so the property is exercised");
    }
}
