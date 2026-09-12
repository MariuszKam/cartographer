package cartographer.cli;

import cartographer.model.DisplayPosition;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public class WhereamiCommand
        implements Command {

    private final PrintStream out;
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;

    public WhereamiCommand(
            PrintStream out,
            VcdbsReader reader,
            WorldMetadataReader metadataReader
    ) {
        this.out = out;
        this.reader = reader;
        this.metadataReader = metadataReader;
    }

    @Override
    public void run(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: whereami <save.vcdbs> "
                            + "[--player <id-or-name>]"
            );
        }

        Path savePath =
                Path.of(
                        args[0]
                );

        Optional<String> playerSelector =
                playerSelector(
                        args
                );

        ProgressReporter progress =
                new ProgressReporter(
                        out
                );

        WorldPosition absolutePosition =
                playerSelector
                        .map(
                                selector ->
                                        reader.readPlayerPosition(
                                                savePath,
                                                selector,
                                                progress
                                        )
                        )
                        .orElseGet(
                                () ->
                                        reader.readPlayerPosition(
                                                savePath,
                                                progress
                                        )
                        );

        WorldMetadata metadata =
                metadataReader.read(
                        savePath,
                        progress
                );

        DisplayPosition displayPosition =
                metadata.toDisplay(
                        absolutePosition
                );

        out.println();
        out.println(
                "PLAYER"
        );
        out.println();

        out.println(
                "Game position:"
        );

        out.printf(
                Locale.ROOT,
                "X: %.3f%n",
                displayPosition.x()
        );

        out.printf(
                Locale.ROOT,
                "Y: %.3f%n",
                displayPosition.y()
        );

        out.printf(
                Locale.ROOT,
                "Z: %.3f%n",
                displayPosition.z()
        );

        out.println();

        out.println(
                "Save position:"
        );

        out.printf(
                Locale.ROOT,
                "X: %.3f%n",
                absolutePosition.x()
        );

        out.printf(
                Locale.ROOT,
                "Y: %.3f%n",
                absolutePosition.y()
        );

        out.printf(
                Locale.ROOT,
                "Z: %.3f%n",
                absolutePosition.z()
        );

        out.println();

        out.printf(
                "World size: %d x %d x %d%n",
                metadata.mapSizeX(),
                metadata.mapSizeY(),
                metadata.mapSizeZ()
        );

        out.printf(
                "Save Chunk: %d, %d%n",
                absolutePosition.chunkCoordinate().x(),
                absolutePosition.chunkCoordinate().z()
        );

        out.printf(
                "Save MapChunk: %d, %d%n",
                absolutePosition.mapChunkCoordinate().x(),
                absolutePosition.mapChunkCoordinate().z()
        );

        out.printf(
                "Save Region: %d, %d%n",
                absolutePosition
                        .mapChunkCoordinate()
                        .regionCoordinate()
                        .x(),
                absolutePosition
                        .mapChunkCoordinate()
                        .regionCoordinate()
                        .z()
        );
    }

    private Optional<String> playerSelector(
            String[] args
    ) {
        for (int index = 1;
             index < args.length - 1;
             index++) {

            if ("--player".equals(
                    args[index]
            )) {
                return Optional.of(
                        args[index + 1]
                );
            }
        }

        return Optional.empty();
    }
}