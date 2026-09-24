package cartographer.cli;

import cartographer.marker.MarkerStore;
import cartographer.marker.UserMarker;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkerCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void addAcceptsMultiWordNameWithoutNestedQuotes() {
        MarkerStore store =
                store();

        Path savePath =
                tempDir.resolve(
                        "world.vcdbs"
                );

        MarkerCommand command =
                command(
                        store,
                        "add"
                );

        command.run(
                new String[]{
                        savePath.toString(),
                        "RED",
                        "CLAY",
                        "-834",
                        "259"
                }
        );

        assertEquals(
                List.of(
                        new UserMarker(
                                "RED CLAY",
                                -834.0,
                                259.0
                        )
                ),
                store.load(
                        savePath
                )
        );
    }

    @Test
    void updateChangesCoordinatesWithoutCreatingDuplicate() {
        MarkerStore store =
                store();

        Path savePath =
                tempDir.resolve(
                        "world.vcdbs"
                );

        store.put(
                savePath,
                new UserMarker(
                        "RED CLAY",
                        -834.0,
                        259.0
                )
        );

        MarkerCommand command =
                command(
                        store,
                        "update"
                );

        command.run(
                new String[]{
                        savePath.toString(),
                        "RED",
                        "CLAY",
                        "-800",
                        "300"
                }
        );

        assertEquals(
                List.of(
                        new UserMarker(
                                "RED CLAY",
                                -800.0,
                                300.0
                        )
                ),
                store.load(
                        savePath
                )
        );
    }

    @Test
    void removeAcceptsMultiWordName() {
        MarkerStore store =
                store();

        Path savePath =
                tempDir.resolve(
                        "world.vcdbs"
                );

        store.put(
                savePath,
                new UserMarker(
                        "BLUE CLAY",
                        -579.0,
                        337.0
                )
        );

        MarkerCommand command =
                command(
                        store,
                        "remove"
                );

        command.run(
                new String[]{
                        savePath.toString(),
                        "BLUE",
                        "CLAY"
                }
        );

        assertTrue(
                store.load(
                                savePath
                        )
                        .isEmpty()
        );
    }

    @Test
    void clearRemovesOnlyMarkersFromRequestedSave() {
        MarkerStore store =
                store();

        Path firstSave =
                tempDir.resolve(
                        "first.vcdbs"
                );

        Path secondSave =
                tempDir.resolve(
                        "second.vcdbs"
                );

        store.put(
                firstSave,
                new UserMarker(
                        "FIRST",
                        1.0,
                        2.0
                )
        );

        store.put(
                secondSave,
                new UserMarker(
                        "SECOND",
                        3.0,
                        4.0
                )
        );

        MarkerCommand command =
                command(
                        store,
                        "clear"
                );

        command.run(
                new String[]{
                        firstSave.toString()
                }
        );

        assertTrue(
                store.load(
                                firstSave
                        )
                        .isEmpty()
        );

        assertEquals(
                List.of(
                        new UserMarker(
                                "SECOND",
                                3.0,
                                4.0
                        )
                ),
                store.load(
                        secondSave
                )
        );
    }

    private MarkerStore store() {
        return new MarkerStore(
                tempDir
        );
    }

    private MarkerCommand command(
            MarkerStore store,
            String subcommand
    ) {
        VcdbsReader reader = reader();
        return new MarkerCommand(
                new PrintStream(
                        new ByteArrayOutputStream()
                ),
                store,
                reader,
                new SaveSessionFactory(
                        new SqliteSaveConnection(),
                        reader,
                        new WorldMetadataReader()
                ),
                subcommand
        );
    }

    private VcdbsReader reader() {
        return new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                new ChunkParser(),
                new RegistryParser()
        );
    }
}