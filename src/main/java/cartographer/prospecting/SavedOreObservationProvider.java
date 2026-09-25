package cartographer.prospecting;

import cartographer.model.WorldPosition;
import cartographer.cache.RenderDataCacheStore;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.resource.SnapshotResourceReader;
import cartographer.snapshot.SnapshotUpperRockReader;
import cartographer.snapshot.SnapshotWorldHeaderReader;
import cartographer.save.VcdbsReader;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class SavedOreObservationProvider implements FusedProspectingObservationProvider {
    private final FusedProspectingEngine fusedEngine;
    private final SaveSessionFactory sessionFactory;
    private final SnapshotUpperRockReader snapshotRockReader;
    private final SnapshotResourceReader snapshotResourceReader;
    private final SnapshotWorldHeaderReader snapshotHeaderReader;

    public SavedOreObservationProvider(
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            RenderDataCacheStore renderDataCacheStore
    ) {
        VcdbsReader requiredReader = Objects.requireNonNull(
                reader,
                "reader is required"
        );
        this.fusedEngine = new FusedProspectingEngine(requiredReader);
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "session factory is required"
        );
        RenderDataCacheStore cache = Objects.requireNonNull(
                renderDataCacheStore,
                "render data cache store is required"
        );
        this.snapshotRockReader = new SnapshotUpperRockReader(cache);
        this.snapshotResourceReader = new SnapshotResourceReader(cache);
        this.snapshotHeaderReader = new SnapshotWorldHeaderReader(cache);
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

        var metadata = session.snapshot().metadata();
        var registry = session.snapshot().blockRegistry();
        var rockMap = snapshotRockReader.read(
                session.savePath(),
                metadata,
                registry,
                center,
                radius
        );
        var observations = snapshotResourceReader.readObservations(
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

        var header = snapshotHeaderReader.read(savePath);
        if (header.isPresent()) {
            var metadata = header.orElseThrow().metadata();
            var registry = header.orElseThrow().blockRegistry();
            var rockMap = snapshotRockReader.read(
                    savePath,
                    metadata,
                    registry,
                    center,
                    radius
            );
            var observations = snapshotResourceReader.readObservations(
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

        try (SaveSession session = sessionFactory.open(savePath)) {
            return fusedEngine.analyze(
                    session,
                    center,
                    radius,
                    resourceKeys
            );
        }
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
