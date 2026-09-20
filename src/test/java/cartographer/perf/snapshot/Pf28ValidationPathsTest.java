package cartographer.perf.snapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pf28ValidationPathsTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsFreshSiblingEvidenceDirectory() throws Exception {
        Path sourceDirectory = Files.createDirectory(
                temporaryDirectory.resolve("save")
        );
        Path save = Files.writeString(
                sourceDirectory.resolve("world.vcdbs"),
                "source"
        );
        Path evidence = temporaryDirectory.resolve("evidence");

        Pf28ValidationPaths paths =
                Pf28ValidationPaths.prepare(save, evidence);

        assertEquals(save.toAbsolutePath().normalize(), paths.save());
        assertEquals(
                evidence.toAbsolutePath().normalize(),
                paths.outputRoot()
        );
        assertTrue(Files.isDirectory(paths.outputRoot()));
    }

    @Test
    void acceptsAlreadyCreatedButEmptyEvidenceDirectory() throws Exception {
        Path sourceDirectory = Files.createDirectory(
                temporaryDirectory.resolve("save")
        );
        Path save = Files.writeString(
                sourceDirectory.resolve("world.vcdbs"),
                "source"
        );
        Path evidence = Files.createDirectory(
                temporaryDirectory.resolve("evidence")
        );

        Pf28ValidationPaths paths =
                Pf28ValidationPaths.prepare(save, evidence);

        assertEquals(
                evidence.toAbsolutePath().normalize(),
                paths.outputRoot()
        );
    }

    @Test
    void rejectsEvidenceInsideSourceDirectory() throws Exception {
        Path sourceDirectory = Files.createDirectory(
                temporaryDirectory.resolve("save")
        );
        Path save = Files.writeString(
                sourceDirectory.resolve("world.vcdbs"),
                "source"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Pf28ValidationPaths.prepare(
                        save,
                        sourceDirectory.resolve("evidence")
                )
        );
    }

    @Test
    void rejectsEvidenceRootThatContainsSourceDirectory() throws Exception {
        Path evidenceAncestor = Files.createDirectory(
                temporaryDirectory.resolve("container")
        );
        Path sourceDirectory = Files.createDirectory(
                evidenceAncestor.resolve("save")
        );
        Path save = Files.writeString(
                sourceDirectory.resolve("world.vcdbs"),
                "source"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> Pf28ValidationPaths.prepare(
                        save,
                        evidenceAncestor
                )
        );
    }

    @Test
    void rejectsNonEmptyEvidenceDirectory() throws Exception {
        Path sourceDirectory = Files.createDirectory(
                temporaryDirectory.resolve("save")
        );
        Path save = Files.writeString(
                sourceDirectory.resolve("world.vcdbs"),
                "source"
        );
        Path evidence = Files.createDirectory(
                temporaryDirectory.resolve("evidence")
        );
        Files.writeString(evidence.resolve("old-report.txt"), "stale");

        assertThrows(
                IllegalArgumentException.class,
                () -> Pf28ValidationPaths.prepare(save, evidence)
        );
    }
}
