package cartographer.application;

import cartographer.model.ServerMapRegion;
import cartographer.model.WorldPosition;
import cartographer.resource.ResourceAnalyzer;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SaveSnapshot;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Loads immutable Workstation world-overview data through one operation-scoped SaveSession.
 */
public final class LoadWorldOverviewUseCase {
    private final VcdbsReader reader;
    private final ResourceAnalyzer resourceAnalyzer;
    private final SaveSessionFactory sessionFactory;

    public LoadWorldOverviewUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            ResourceAnalyzer resourceAnalyzer
    ) {
        this(
                reader,
                resourceAnalyzer,
                new SaveSessionFactory(
                        new SqliteSaveConnection(),
                        reader,
                        metadataReader
                )
        );
    }

    LoadWorldOverviewUseCase(
            VcdbsReader reader,
            ResourceAnalyzer resourceAnalyzer,
            SaveSessionFactory sessionFactory
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.resourceAnalyzer = Objects.requireNonNull(
                resourceAnalyzer,
                "resource analyzer is required"
        );
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "session factory is required"
        );
    }

    public WorldOverview execute(Path savePath) {
        Objects.requireNonNull(savePath, "savePath is required");
        try (SaveSession session = sessionFactory.open(savePath)) {
            SaveSnapshot snapshot = session.snapshot();
            ReadDiagnostics mapRegionDiagnostics = new ReadDiagnostics();
            List<ServerMapRegion> regions = reader.readMapRegions(
                    session,
                    mapRegionDiagnostics,
                    ProgressReporter.NONE
            );
            Optional<WorldPosition> playerAbsolute;
            try {
                playerAbsolute = Optional.of(
                        reader.readPlayerPosition(session, ProgressReporter.NONE)
                );
            } catch (RuntimeException exception) {
                playerAbsolute = Optional.empty();
            }
            return new WorldOverview(
                    snapshot.metadata(),
                    snapshot.blockRegistry(),
                    playerAbsolute,
                    resourceAnalyzer.resourceKeys(regions)
            );
        }
    }
}
