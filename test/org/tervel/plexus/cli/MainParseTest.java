package org.tervel.plexus.cli;

import org.tervel.plexus.descriptor.*;
import org.tervel.plexus.router.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The CLI's data-file reader: a file reads exactly or is refused, with the line that is wrong. */
final class MainParseTest {

    @TempDir
    Path dir;

    private Path file(String... lines) throws IOException {
        final var p = dir.resolve("data.txt");
        Files.write(p, List.of(lines));
        return p;
    }

    private String refused(String... lines) throws IOException {
        final var p = file(lines);
        return assertThrows(IllegalArgumentException.class, () -> Main.parse(p)).getMessage();
    }

    @Test
    void theBundledDataFilesReadInFull() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("data")).filter(f -> f.toString().endsWith(".txt"))) {
            files.forEach(f -> {
                try {
                    assertTrue(!Main.parse(f).isEmpty(), f + " has examples");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    @Test
    void aRecordReadsItsCellsAndLabel() throws IOException {
        final var examples = Main.parse(file("2x2", "x.", "..", "1"));
        assertEquals(1, examples.size());
        assertEquals(1, examples.get(0).label());
        assertArrayEquals(new int[]{1, 0, 0, 0}, examples.get(0).grid());
    }

    @Test
    void aSymbolOtherThanXOrDotIsRefusedWithItsLine() throws IOException {
        assertTrue(refused("2x2", "1.", "..", "0").contains(":2: cell '1'"));
        assertTrue(refused("2x2", "X.", "..", "0").contains("cell 'X'"));
    }

    @Test
    void aRaggedRowIsRefused() throws IOException {
        assertTrue(refused("2x2", "xx.", "..", "0").contains("row has 3 cells, the frame is 2 wide"));
    }

    @Test
    void aLabelOtherThanZeroOrOneIsRefused() throws IOException {
        assertTrue(refused("2x2", "x.", "..", "2").contains("label must be 0 or 1, not \"2\""));
    }

    @Test
    void aRecordCutShortIsRefused() throws IOException {
        assertTrue(refused("2x2", "x.", "..").contains("no label"));
        assertTrue(refused("2x2", "x.").contains("ends inside a record"));
    }

    @Test
    void aBlankLineBetweenRecordsIsRefused() throws IOException {
        assertTrue(refused("2x2", "x.", "..", "0", "", "x.", "..", "1").contains(":5: row has 0 cells"));
    }

    @Test
    void aMalformedHeaderIsRefused() throws IOException {
        assertTrue(refused("3", "x..", "...", "...", "0").contains("the header must be rows x columns"));
    }
}
