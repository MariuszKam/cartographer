package cartographer.application;

import cartographer.cache.RenderDataCacheStore;
import cartographer.cache.SurfaceCacheTile;
import cartographer.cache.SurfaceTileLookup;
import cartographer.cache.SurfaceTileStore;
import cartographer.cache.TerrainHeightTile;
import cartographer.cache.TerrainTileLookup;
import cartographer.cache.TerrainTileStore;
import cartographer.model.MapChunkCoordinate;
import cartographer.model.WorldMetadata;
import cartographer.scanner.SurfaceMap;
import cartographer.scanner.SurfaceRainHeightScanResult;
import cartographer.scanner.SurfaceTile;
import cartographer.scanner.SurfaceTileDiagnosticSummary;
import cartographer.snapshot.WorldDataSnapshot;
import cartographer.snapshot.WorldIndexCatalogStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Owns render-data cache lookup, publication, and diagnostics for live map preparation. */
final class LiveMapDataCache {
    private static final int CACHE_WRITE_BATCH_SIZE = 128;

    private final Optional<RenderDataCacheStore> renderDataCacheStore;

    LiveMapDataCache(Optional<RenderDataCacheStore> renderDataCacheStore) {
        this.renderDataCacheStore = Objects.requireNonNull(
                renderDataCacheStore,
                "renderDataCacheStore"
        );
    }

    Context open(Path savePath, boolean cacheAllowed) {
        if (!cacheAllowed) {
            return Context.disabled(
                    "foliage-inclusive Surface analysis bypasses render-data cache"
            );
        }
        if (renderDataCacheStore.isEmpty()) {
            return Context.disabled("render-data cache disabled");
        }
        try {
            RenderDataCacheStore store = renderDataCacheStore.orElseThrow();
            Optional<WorldDataSnapshot> snapshot =
                    WorldDataSnapshot.openOrCreate(store, savePath);
            if (snapshot.isEmpty()) {
                return Context.disabled(
                        "world-data snapshot unavailable or incompatible manifest"
                );
            }
            WorldDataSnapshot world = snapshot.orElseThrow();
            return Context.enabled(
                    world.terrainStore(),
                    world.surfaceStore(),
                    world.indexCatalogStore()
            );
        } catch (RuntimeException exception) {
            return Context.disabled(
                    "world-data snapshot unavailable: " + exception.getMessage()
            );
        }
    }

    Map<MapChunkCoordinate, TerrainTileLookup> lookupTerrain(
            Context context,
            List<MapChunkCoordinate> coordinates
    ) {
        context.terrain.requested = coordinates.size();
        if (!context.enabled) {
            return misses(coordinates);
        }
        Map<MapChunkCoordinate, TerrainTileLookup> lookups;
        try {
            lookups = context.terrainStore.orElseThrow().read(coordinates);
        } catch (RuntimeException exception) {
            context.disableWrites(
                    "terrain cache read disabled: " + exception.getMessage()
            );
            lookups = new LinkedHashMap<>();
            for (MapChunkCoordinate coordinate : coordinates) {
                lookups.put(coordinate, TerrainTileLookup.corrupt());
            }
        }
        for (TerrainTileLookup lookup : lookups.values()) {
            switch (lookup.status()) {
                case HIT -> context.terrain.hits++;
                case MISS -> context.terrain.misses++;
                case CORRUPT -> context.terrain.corruptOrIncompatible++;
            }
        }
        return lookups;
    }

    Map<MapChunkCoordinate, SurfaceTileLookup> lookupSurface(
            Context context,
            List<MapChunkCoordinate> coordinates,
            WorldMetadata metadata
    ) {
        context.surface.requested = coordinates.size();
        if (!context.enabled) {
            return surfaceMisses(coordinates);
        }
        Map<MapChunkCoordinate, SurfaceTileLookup> raw;
        try {
            raw = context.surfaceStore.orElseThrow().read(coordinates);
        } catch (RuntimeException exception) {
            context.disableWrites(
                    "Surface cache read disabled: " + exception.getMessage()
            );
            raw = new LinkedHashMap<>();
            for (MapChunkCoordinate coordinate : coordinates) {
                raw.put(coordinate, SurfaceTileLookup.corrupt());
            }
        }

        Map<MapChunkCoordinate, SurfaceTileLookup> result = new LinkedHashMap<>();
        for (MapChunkCoordinate coordinate : coordinates) {
            SurfaceTileLookup lookup =
                    raw.getOrDefault(coordinate, SurfaceTileLookup.miss());
            if (lookup.status() == SurfaceTileLookup.Status.HIT
                    && !lookup.tile().matchesWorld(metadata)) {
                context.surface.corruptOrIncompatible++;
                context.surface.worldMismatches++;
                result.put(coordinate, SurfaceTileLookup.corrupt());
            } else {
                result.put(coordinate, lookup);
                switch (lookup.status()) {
                    case HIT -> context.surface.hits++;
                    case MISS -> context.surface.misses++;
                    case CORRUPT -> context.surface.corruptOrIncompatible++;
                }
            }
        }
        return result;
    }

    void publishTerrain(Context context, List<TerrainHeightTile> buffer) {
        if (buffer.isEmpty()) return;
        if (context.enabled && context.writesEnabled) {
            try {
                context.terrainStore.orElseThrow().publish(List.copyOf(buffer));
                context.terrain.published =
                        Math.addExact(context.terrain.published, buffer.size());
            } catch (RuntimeException exception) {
                context.disableWrites(
                        "terrain cache write disabled: " + exception.getMessage()
                );
            }
        }
        buffer.clear();
    }

    void publishSurfaceTiles(
            Context context,
            WorldMetadata metadata,
            SurfaceMap map,
            SurfaceRainHeightScanResult result,
            Set<MapChunkCoordinate> surfaceMisses,
            Set<MapChunkCoordinate> surfacePlanningInputsAvailable
    ) {
        if (!context.enabled || !context.writesEnabled || surfaceMisses.isEmpty()) {
            return;
        }
        Set<MapChunkCoordinate> fallback = new HashSet<>(result.fallbackMapChunks());
        List<SurfaceCacheTile> buffer = new ArrayList<>(CACHE_WRITE_BATCH_SIZE);

        for (MapChunkCoordinate coordinate : surfaceMisses) {
            boolean fallbackMode = fallback.contains(coordinate);
            SurfaceTileDiagnosticSummary fallbackSummary = fallbackMode
                    ? result.fallbackDiagnosticsByMapChunk().get(coordinate)
                    : null;

            boolean publishableWithoutPlanningInput =
                    fallbackMode && fallbackSummary != null;
            if (!surfacePlanningInputsAvailable.contains(coordinate)
                    && !publishableWithoutPlanningInput) {
                context.surface.skippedIncompleteForPublish++;
                continue;
            }
            try {
                SurfaceTile tile = map.tileAt(
                        map.layout().tileIndex(coordinate.x(), coordinate.z())
                );
                if (fallbackMode && fallbackSummary == null) {
                    context.surface.skippedIncompleteForPublish++;
                    continue;
                }
                SurfaceCacheTile cached = SurfaceCacheTile.fromComplete(
                        tile,
                        metadata,
                        fallbackMode
                                ? SurfaceCacheTile.SourceMode.FALLBACK
                                : SurfaceCacheTile.SourceMode.RAIN_HEIGHT_FAST,
                        fallbackMode
                                ? fallbackSummary.columnsScanned()
                                : countActiveConsidered(tile),
                        fallbackMode ? fallbackSummary.emptyColumns() : 0,
                        fallbackMode
                                ? fallbackSummary.liquidUnavailableColumns()
                                : 0
                );
                buffer.add(cached);
                if (buffer.size() >= CACHE_WRITE_BATCH_SIZE) {
                    publishSurface(context, buffer);
                }
            } catch (RuntimeException exception) {
                context.surface.skippedIncompleteForPublish++;
            }
        }
        publishSurface(context, buffer);
    }

    private void publishSurface(Context context, List<SurfaceCacheTile> buffer) {
        if (buffer.isEmpty()) return;
        if (context.writesEnabled) {
            try {
                context.surfaceStore.orElseThrow().publish(List.copyOf(buffer));
                context.surface.published =
                        Math.addExact(context.surface.published, buffer.size());
            } catch (RuntimeException exception) {
                context.disableWrites(
                        "Surface cache write disabled: " + exception.getMessage()
                );
            }
        }
        buffer.clear();
    }

    private static int countActiveConsidered(SurfaceTile tile) {
        int count = 0;
        for (int localZ = 0; localZ < tile.height(); localZ++) {
            for (int localX = 0; localX < tile.width(); localX++) {
                if (tile.isActive(localX, localZ)
                        && tile.isConsidered(localX, localZ)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Map<MapChunkCoordinate, TerrainTileLookup> misses(
            List<MapChunkCoordinate> coordinates
    ) {
        Map<MapChunkCoordinate, TerrainTileLookup> result = new LinkedHashMap<>();
        coordinates.forEach(
                coordinate -> result.put(coordinate, TerrainTileLookup.miss())
        );
        return result;
    }

    private static Map<MapChunkCoordinate, SurfaceTileLookup> surfaceMisses(
            List<MapChunkCoordinate> coordinates
    ) {
        Map<MapChunkCoordinate, SurfaceTileLookup> result = new LinkedHashMap<>();
        coordinates.forEach(
                coordinate -> result.put(coordinate, SurfaceTileLookup.miss())
        );
        return result;
    }

    static final class Context {
        private final boolean enabled;
        private final Optional<TerrainTileStore> terrainStore;
        private final Optional<SurfaceTileStore> surfaceStore;
        private final Optional<WorldIndexCatalogStore> indexCatalogStore;
        private final CacheCounters terrain = new CacheCounters();
        private final CacheCounters surface = new CacheCounters();
        private final List<String> notes = new ArrayList<>();
        private boolean writesEnabled;

        private Context(
                boolean enabled,
                Optional<TerrainTileStore> terrainStore,
                Optional<SurfaceTileStore> surfaceStore,
                Optional<WorldIndexCatalogStore> indexCatalogStore,
                String note
        ) {
            this.enabled = enabled;
            this.terrainStore = terrainStore;
            this.surfaceStore = surfaceStore;
            this.indexCatalogStore = indexCatalogStore;
            this.writesEnabled = enabled;
            if (note != null && !note.isBlank()) {
                notes.add(note);
            }
        }

        private static Context enabled(
                TerrainTileStore terrainStore,
                SurfaceTileStore surfaceStore,
                WorldIndexCatalogStore indexCatalogStore
        ) {
            return new Context(
                    true,
                    Optional.of(terrainStore),
                    Optional.of(surfaceStore),
                    Optional.of(indexCatalogStore),
                    null
            );
        }

        private static Context disabled(String note) {
            return new Context(
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    note
            );
        }

        Set<MapChunkCoordinate> knownAbsent(Collection<MapChunkCoordinate> coordinates) {
            if (!enabled || indexCatalogStore.isEmpty() || coordinates.isEmpty()) {
                return Set.of();
            }
            try {
                WorldIndexCatalogStore catalog = indexCatalogStore.orElseThrow();
                if (!catalog.mapChunkScanComplete()) {
                    return Set.of();
                }
                Set<MapChunkCoordinate> observed = catalog.observedAmong(coordinates);
                LinkedHashSet<MapChunkCoordinate> absent =
                        new LinkedHashSet<>(coordinates);
                absent.removeAll(observed);
                if (!absent.isEmpty()) {
                    notes.add(
                            "world snapshot catalog skipped "
                                    + absent.size()
                                    + " known-unobserved mapchunk source lookups"
                    );
                }
                return Set.copyOf(absent);
            } catch (RuntimeException exception) {
                notes.add(
                        "world snapshot catalog optimization unavailable: "
                                + exception.getMessage()
                );
                return Set.of();
            }
        }

        void recordTerrainSourceLoaded() {
            terrain.sourceLoaded++;
        }

        void recordSurfaceSourceLoaded(int count) {
            surface.sourceLoaded = Math.addExact(surface.sourceLoaded, count);
        }

        void addNote(String note) {
            if (note != null && !note.isBlank()) {
                notes.add(note);
            }
        }

        RenderDataCacheReport report() {
            return new RenderDataCacheReport(
                    enabled,
                    terrain.toStats(),
                    surface.toStats(),
                    notes
            );
        }

        private void disableWrites(String note) {
            writesEnabled = false;
            addNote(note);
        }
    }

    private static final class CacheCounters {
        private int requested;
        private int hits;
        private int misses;
        private int corruptOrIncompatible;
        private int sourceLoaded;
        private int published;
        private int skippedIncompleteForPublish;
        private int worldMismatches;

        private RenderDataCacheReport.ArtifactStats toStats() {
            return new RenderDataCacheReport.ArtifactStats(
                    requested,
                    hits,
                    misses,
                    corruptOrIncompatible,
                    sourceLoaded,
                    published,
                    skippedIncompleteForPublish,
                    worldMismatches
            );
        }
    }
}
