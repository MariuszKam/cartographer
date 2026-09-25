package cartographer.prospecting;

import cartographer.model.WorldPosition;
import cartographer.cache.RenderDataCacheStore;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.resource.SnapshotResourceReader;
import cartographer.snapshot.SnapshotUpperRockReader;
import cartographer.snapshot.SnapshotWorldHeaderReader;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SavedOreObservationProvider implements FusedProspectingObservationProvider {
    private final FusedProspectingEngine fusedEngine;
    private final Optional<SnapshotUpperRockReader> snapshotRockReader;
    private final Optional<SnapshotResourceReader> snapshotResourceReader;
    private final Optional<SnapshotWorldHeaderReader> snapshotHeaderReader;

    public SavedOreObservationProvider(
            VcdbsReader reader,
            WorldMetadataReader metadataReader
    ) {
        this(
                reader,
                metadataReader,
                Optional.empty()
        );
    }

    public SavedOreObservationProvider(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            RenderDataCacheStore renderDataCacheStore
    ) {
        this(
                reader,
                metadataReader,
                Optional.of(Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache store is required"
                ))
        );
    }

    public SavedOreObservationProvider(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            SaveSessionFactory sessionFactory
    ) {
        this(
                reader,
                metadataReader,
                sessionFactory,
                Optional.empty()
        );
    }

    public SavedOreObservationProvider(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            SaveSessionFactory sessionFactory,
            RenderDataCacheStore renderDataCacheStore
    ) {
        this(
                reader,
                metadataReader,
                sessionFactory,
                Optional.of(Objects.requireNonNull(
                        renderDataCacheStore,
                        "render data cache store is required"
                ))
        );
    }

    private SavedOreObservationProvider(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        this(
                reader,
                metadataReader,
                new SaveSessionFactory(
                        new SqliteSaveConnection(),
                        reader,
                        metadataReader
                ),
                renderDataCacheStore
        );
    }

    private SavedOreObservationProvider(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            SaveSessionFactory sessionFactory,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        this.fusedEngine = new FusedProspectingEngine(
                Objects.requireNonNull(reader, "reader is required"),
                Objects.requireNonNull(
                        sessionFactory,
                        "session factory is required"
                )
        );
        Objects.requireNonNull(
                metadataReader,
                "metadata reader is required"
        );
        Optional<RenderDataCacheStore> cache = Objects.requireNonNull(
                renderDataCacheStore,
                "render data cache option is required"
        );
        this.snapshotRockReader = cache.map(SnapshotUpperRockReader::new);
        this.snapshotResourceReader = cache.map(SnapshotResourceReader::new);
        this.snapshotHeaderReader = cache.map(SnapshotWorldHeaderReader::new);
    }

    @Override
    public FusedProspectingResult analyze(
            SaveSession session,
            WorldPosition center,
            int radius,
            List<String> resourceKeys
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(center, "center is required");
        resourceKeys = List.copyOf(
                Objects.requireNonNull(
                        resourceKeys,
                        "resourceKeys are required"
                )
        );

        if (snapshotRockReader.isPresent()
                && snapshotResourceReader.isPresent()) {
            var metadata = session.snapshot().metadata();
            var registry = session.snapshot().blockRegistry();
            var rockMap = snapshotRockReader.orElseThrow().read(
                    session.savePath(),
                    metadata,
                    registry,
                    center,
                    radius
            );
            var observations =
                    snapshotResourceReader.orElseThrow().readObservations(
                            session.savePath(),
                            metadata,
                            registry,
                            floor(center.x()),
                            floor(center.z()),
                            radius,
                            resourceKeys
                    );
            if (rockMap.isPresent() && observations.isPresent()) {
                return new FusedProspectingResult(
                        rockMap.orElseThrow(),
                        observations.orElseThrow()
                );
            }
        }

        return fusedEngine.analyze(
                session,
                center,
                radius,
                resourceKeys
        );
    }

    @Override
    public FusedProspectingResult analyze(
            Path savePath,
            WorldPosition center,
            int radius,
            List<String> resourceKeys
    ) {
        Objects.requireNonNull(savePath, "savePath is required");
        Objects.requireNonNull(center, "center is required");
        resourceKeys = List.copyOf(Objects.requireNonNull(
                resourceKeys,
                "resourceKeys are required"
        ));

        if (snapshotRockReader.isPresent()
                && snapshotResourceReader.isPresent()
                && snapshotHeaderReader.isPresent()) {
            var header = snapshotHeaderReader.orElseThrow().read(savePath);
            if (header.isPresent()) {
                var metadata = header.orElseThrow().metadata();
                var registry = header.orElseThrow().blockRegistry();
                var rockMap = snapshotRockReader.orElseThrow().read(
                        savePath,
                        metadata,
                        registry,
                        center,
                        radius
                );
                var observations =
                        snapshotResourceReader.orElseThrow().readObservations(
                                savePath,
                                metadata,
                                registry,
                                floor(center.x()),
                                floor(center.z()),
                                radius,
                                resourceKeys
                        );
                if (rockMap.isPresent() && observations.isPresent()) {
                    return new FusedProspectingResult(
                            rockMap.orElseThrow(),
                            observations.orElseThrow()
                    );
                }
            }
        }

        return fusedEngine.analyze(
                savePath,
                center,
                radius,
                resourceKeys
        );
    }

    private int floor(double value) {
        double floored = Math.floor(value);
        if (floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "world center is outside the supported block range"
            );
        }
        return (int) floored;
    }

}
