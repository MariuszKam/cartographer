package cartographer.perf.macro;

import cartographer.application.OreChunkPositionPlanner;
import cartographer.application.RenderActualOreMapRequest;
import cartographer.application.RenderActualOreMapResult;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.application.RenderRockMapRequest;
import cartographer.application.RenderRockMapResult;
import cartographer.application.RenderRockMapUseCase;
import cartographer.geology.rock.RockMapMode;
import cartographer.marker.MarkerStore;
import cartographer.navigation.HomeStore;
import cartographer.perf.RenderDataCacheRevision;
import cartographer.perf.RenderDataCacheStore;
import cartographer.perf.SurfaceTileStore;
import cartographer.perf.TerrainTileStore;
import cartographer.perf.fingerprint.ImageFingerprinter;
import cartographer.perf.fingerprint.RenderActualOreMapResultFingerprinter;
import cartographer.perf.workload.WorkloadFamily;
import cartographer.perf.workload.WorkloadSpec;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.render.RockMapRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockYFilter;
import cartographer.scanner.MultiActualBlockMapScanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.ArrayList;

/** Production MAP and ROCK operations used by the reviewer-controlled runner. */
public final class Pf18ProductionOperationFactory implements Pf18MacroOperationFactory {
    private final Path stateRoot;

    public Pf18ProductionOperationFactory(Path stateRoot) {
        this.stateRoot = stateRoot.toAbsolutePath().normalize();
    }

    @Override
    public Pf18MacroOperation create(Path save, Path cacheRoot, WorkloadSpec workload) {
        VcdbsReader reader = new VcdbsReader(new PlayerDataParser(), new MapChunkParser(),
                new ChunkParser(), new RegistryParser());
        if (workload.family() == WorkloadFamily.MAP) {
            RenderActualOreMapUseCase useCase = mapUseCase(reader, save, cacheRoot);
            RenderActualOreMapRequest request = new RenderActualOreMapRequest(
                    save, workload.radius().blocks(), 1, RenderStyle.TOPOGRAPHIC,
                    Set.of(RenderLayer.TERRAIN, RenderLayer.SURFACE), Optional.empty(),
                    ActualBlockYFilter.unbounded(), Optional.empty());
            return () -> mapEvidence(useCase.execute(request));
        }
        if (workload.family() == WorkloadFamily.ROCK_UPPER) {
            WorldMetadataReader metadataReader = new WorldMetadataReader();
            SaveSessionFactory sessions = new SaveSessionFactory(
                    new SqliteSaveConnection(), reader, metadataReader);
            RenderRockMapUseCase useCase = new RenderRockMapUseCase(
                    reader, new RockMapRenderer(), sessions);
            RenderRockMapRequest request = new RenderRockMapRequest(
                    save, RockMapMode.UPPER_ROCK, workload.radius().blocks(),
                    Optional.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty());
            return () -> rockEvidence(useCase.execute(request));
        }
        throw new IllegalArgumentException("Unsupported PF-1.8 workload family: "
                + workload.family());
    }

    @Override
    public void prepareCache(Path save, Path cacheRoot, WorkloadSpec workload) {
        create(save, cacheRoot, workload).execute();
    }

    @Override
    public List<String> cacheEvidence(Path save, Path cacheRoot, WorkloadSpec workload) {
        if (workload.family() != WorkloadFamily.MAP) return List.of();
        RenderDataCacheStore store = new RenderDataCacheStore(cacheRoot);
        RenderDataCacheRevision revision = store.observe(save);
        List<String> evidence = new ArrayList<>();
        if (store.find(revision).isPresent()) {
            evidence.add("manifest=" + store.manifestPath(revision).toAbsolutePath().normalize());
        }
        if (regular(new TerrainTileStore(store, revision).databasePath())) {
            evidence.add("terrain-cache=" + new TerrainTileStore(store, revision)
                    .databasePath().toAbsolutePath().normalize());
        }
        if (regular(new SurfaceTileStore(store, revision).databasePath())) {
            evidence.add("surface-cache=" + new SurfaceTileStore(store, revision)
                    .databasePath().toAbsolutePath().normalize());
        }
        return List.copyOf(evidence);
    }

    private RenderActualOreMapUseCase mapUseCase(VcdbsReader reader, Path save, Path cacheRoot) {
        WorldMetadataReader metadataReader = new WorldMetadataReader();
        Path root = cacheRoot == null ? stateRoot.resolve("authoritative") : cacheRoot;
        HomeStore home = new HomeStore(root.resolve("home.properties"));
        MarkerStore markers = new MarkerStore(root);
        SaveSessionFactory sessions = new SaveSessionFactory(
                new SqliteSaveConnection(), reader, metadataReader);
        if (cacheRoot == null) {
            return new RenderActualOreMapUseCase(reader, metadataReader, home, markers,
                    new MapRenderer(), new UserMarkerRenderer(),
                    new ActualOreOverlayPainter(), new MultiActualBlockMapScanner(),
                    new OreChunkPositionPlanner(), sessions);
        }
        return new RenderActualOreMapUseCase(reader, metadataReader, home, markers,
                new MapRenderer(), new UserMarkerRenderer(),
                new ActualOreOverlayPainter(), new MultiActualBlockMapScanner(),
                new OreChunkPositionPlanner(), sessions, new RenderDataCacheStore(cacheRoot));
    }

    private static Pf18IterationEvidence mapEvidence(RenderActualOreMapResult result) {
        var cache = result.renderDataCacheReport();
        boolean hit = cache.enabled() && cache.terrain().hits() > 0 && cache.surface().hits() > 0;
        String work = "terrainSourceLoaded=" + cache.terrain().sourceLoaded()
                + ",surfaceSourceLoaded=" + cache.surface().sourceLoaded();
        return new Pf18IterationEvidence(
                Optional.of(RenderActualOreMapResultFingerprinter.fingerprint(result).sha256Hex()),
                Optional.of(ImageFingerprinter.fingerprint(result.image()).sha256Hex()),
                hit, work);
    }

    private static Pf18IterationEvidence rockEvidence(RenderRockMapResult result) {
        return new Pf18IterationEvidence(
                Optional.empty(),
                Optional.of(ImageFingerprinter.fingerprint(result.rendered().image()).sha256Hex()),
                false, "rock source chunk stream");
    }

    private static boolean regular(Path path) {
        return Files.isRegularFile(path);
    }
}
