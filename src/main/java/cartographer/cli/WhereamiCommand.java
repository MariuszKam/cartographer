package cartographer.cli;

import cartographer.model.WorldPosition;
import cartographer.save.VcdbsReader;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

public class WhereamiCommand implements Command {
    private final PrintStream out;
    private final VcdbsReader reader;

    public WhereamiCommand(PrintStream out, VcdbsReader reader) {
        this.out = out;
        this.reader = reader;
    }

    @Override
    public int run(String[] args) {
        if (args.length < 1) {
            throw new CommandException("Usage: whereami <save.vcdbs> [--player <id-or-name>]");
        }

        Path savePath = Path.of(args[0]);
        Optional<String> playerSelector = option(args, "--player");
        WorldPosition position = reader.readPlayerPosition(savePath, playerSelector);

        out.println("PLAYER");
        out.printf("X: %.3f%n", position.x());
        out.printf("Y: %.3f%n", position.y());
        out.printf("Z: %.3f%n", position.z());
        out.printf("Chunk: %d, %d%n", position.chunkCoordinate().x(), position.chunkCoordinate().z());
        out.printf("MapChunk: %d, %d%n", position.mapChunkCoordinate().x(), position.mapChunkCoordinate().z());
        out.printf("Region: %d, %d%n", position.mapChunkCoordinate().regionCoordinate().x(), position.mapChunkCoordinate().regionCoordinate().z());
        return 0;
    }

    private Optional<String> option(String[] args, String optionName) {
        for (int index = 1; index < args.length - 1; index++) {
            if (optionName.equals(args[index])) {
                return Optional.of(args[index + 1]);
            }
        }
        return Optional.empty();
    }
}
