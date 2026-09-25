package cartographer.application;

import cartographer.environment.EnvironmentInterpreter;
import cartographer.geology.GeologicProvinceInterpreter;
import cartographer.progress.ProgressReporter;
import cartographer.save.MapRegionStreamStats;
import cartographer.save.ReadDiagnostics;
import cartographer.save.SaveSession;
import cartographer.save.VcdbsReader;
import cartographer.snapshot.MapRegionSnapshotEntry;
import cartographer.snapshot.MapRegionSnapshotRead;
import cartographer.snapshot.MapRegionSnapshotStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class MapRegionSnapshotPreparer {
    private static final int BATCH_SIZE = 64;

    private final VcdbsReader reader;
    private final EnvironmentInterpreter environmentInterpreter =
            new EnvironmentInterpreter();
    private final GeologicProvinceInterpreter geologicProvinceInterpreter =
            new GeologicProvinceInterpreter();

    MapRegionSnapshotPreparer(VcdbsReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
    }

    Result prepare(
            SaveSession session,
            MapRegionSnapshotStore store,
            ReadDiagnostics diagnostics,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(session, "session is required");
        Objects.requireNonNull(store, "store is required");
        Objects.requireNonNull(diagnostics, "diagnostics is required");
        Objects.requireNonNull(progress, "progress is required");

        progress.start("Checking snapshot");
        MapRegionSnapshotRead existing = store.readAll();
        if (existing.healthyComplete()) {
            progress.done("Coverage ready");
            return new Result(true, existing.entries().size(), 0);
        }

        store.markScanIncomplete();
        if (existing.corruptRows() > 0) {
            store.clearEntries();
        }

        MutableCount published = new MutableCount();
        List<MapRegionSnapshotEntry> buffer = new ArrayList<>(BATCH_SIZE);
        MapRegionStreamStats stats = reader.forEachObservedMapRegion(
                session,
                diagnostics,
                region -> {
                    buffer.add(new MapRegionSnapshotEntry(
                            region.coordinate(),
                            environmentInterpreter.interpret(region),
                            geologicProvinceInterpreter.summarize(region),
                            region.oreMaps()
                    ));
                    if (buffer.size() >= BATCH_SIZE) {
                        publishBatch(store, buffer, published);
                    }
                },
                progress
        );
        publishBatch(store, buffer, published);

        if (stats.complete()) {
            store.markScanComplete();
        }
        boolean complete = store.readAll().healthyComplete();
        progress.done(complete ? "Coverage ready" : "Coverage partial");
        return new Result(complete, 0, published.value);
    }

    private void publishBatch(
            MapRegionSnapshotStore store,
            List<MapRegionSnapshotEntry> buffer,
            MutableCount published
    ) {
        if (buffer.isEmpty()) {
            return;
        }
        List<MapRegionSnapshotEntry> entries = List.copyOf(buffer);
        store.publish(entries);
        published.value = Math.addExact(published.value, entries.size());
        buffer.clear();
    }

    record Result(boolean coverageComplete, int hits, int published) {
    }

    private static final class MutableCount {
        private int value;
    }
}
