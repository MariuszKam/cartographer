package cartographer.application;

import cartographer.cache.RenderDataCacheStore;
import cartographer.progress.ProgressReporter;
import cartographer.save.SaveSession;
import cartographer.save.SaveSessionFactory;
import cartographer.save.VcdbsReader;

import java.util.Objects;
import java.util.Optional;

/**
 * Routes prepared-map requests between derived snapshot data and the
 * authoritative live-save preparation path.
 */
public final class PrepareMapDataUseCase {
    private final SaveSessionFactory sessionFactory;
    private final Optional<SnapshotPreparedMapDataReader> snapshotReader;
    private final LiveMapDataPreparer livePreparer;


    PrepareMapDataUseCase(
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            Optional<RenderDataCacheStore> renderDataCacheStore
    ) {
        Objects.requireNonNull(reader, "reader is required");
        this.sessionFactory = Objects.requireNonNull(
                sessionFactory,
                "sessionFactory is required"
        );
        Optional<RenderDataCacheStore> cache = Objects.requireNonNull(
                renderDataCacheStore,
                "render data cache option is required"
        );
        this.snapshotReader = cache.map(SnapshotPreparedMapDataReader::new);
        this.livePreparer = new LiveMapDataPreparer(reader, cache);
    }

    public PreparedMapData execute(
            PrepareMapDataRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");

        Optional<PreparedMapData> snapshot =
                executeSnapshot(request, progress);
        if (snapshot.isPresent()) {
            return snapshot.orElseThrow();
        }

        try (SaveSession session = sessionFactory.open(request.savePath())) {
            return execute(session, request, progress);
        }
    }

    public Optional<PreparedMapData> executeSnapshot(
            PrepareMapDataRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(progress, "progress is required");
        if (snapshotReader.isEmpty()) {
            return Optional.empty();
        }
        return snapshotReader.orElseThrow().read(request, progress);
    }

    public PreparedMapData execute(
            SaveSession session,
            PrepareMapDataRequest request,
            ProgressReporter progress
    ) {
        return livePreparer.prepare(session, request, progress);
    }
}
