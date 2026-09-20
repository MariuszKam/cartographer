package cartographer.cli;

import cartographer.application.PrepareWorldSnapshotRequest;
import cartographer.application.PrepareWorldSnapshotResult;
import cartographer.application.PrepareWorldSnapshotUseCase;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;

/** Reviewer-facing PF-2.3 entry point. Workstation UX is deferred to PF-2.7. */
public final class SnapshotCommand implements Command {
    private final PrintStream out;
    private final PrepareWorldSnapshotUseCase useCase;
    private final String subcommand;

    public SnapshotCommand(
            PrintStream out,
            PrepareWorldSnapshotUseCase useCase,
            String subcommand
    ) {
        this.out = Objects.requireNonNull(out, "out is required");
        this.useCase = Objects.requireNonNull(useCase, "useCase is required");
        this.subcommand = Objects.requireNonNull(
                subcommand,
                "subcommand is required"
        );
    }

    @Override
    public void run(String[] args) {
        if (!"prepare".equals(subcommand)) {
            throw new CommandException(
                    "Unknown snapshot subcommand: " + subcommand
            );
        }
        if (args.length != 1) {
            throw new CommandException(
                    "Usage: snapshot prepare <save.vcdbs>"
            );
        }

        Path save = Path.of(args[0]);
        PrepareWorldSnapshotResult result = useCase.execute(
                new PrepareWorldSnapshotRequest(save),
                new ProgressReporter(out)
        );

        out.println();
        out.println("WORLD SNAPSHOT");
        out.println("Revision: " + result.revisionHash());
        out.println("Observed mapchunks: " + result.observedMapChunks());
        out.println(
                "Terrain: hits=" + result.terrainHits()
                        + ", published=" + result.terrainPublished()
                        + ", complete=" + result.terrainCoverageComplete()
        );
        out.println(
                "Surface: hits=" + result.surfaceHits()
                        + ", published=" + result.surfacePublished()
                        + ", skipped-incomplete="
                        + result.surfaceSkippedIncomplete()
                        + ", complete=" + result.surfaceCoverageComplete()
        );
        out.println(
                "Mapregion: hits=" + result.mapRegionHits()
                        + ", published=" + result.mapRegionPublished()
                        + ", complete=" + result.mapRegionCoverageComplete()
        );
        out.println(
                "UPPER_ROCK: hits=" + result.upperRockHits()
                        + ", published=" + result.upperRockPublished()
                        + ", complete=" + result.upperRockCoverageComplete()
        );
        out.println(
                "Catalog complete: " + result.mapChunkCatalogComplete()
        );
        out.println(
                "Snapshot complete: " + result.complete()
        );
    }
}
