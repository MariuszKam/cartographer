package cartographer.application;

import cartographer.coverage.RegionCoverageAnalyzer;
import cartographer.coverage.RegionCoverageRenderer;
import cartographer.coverage.RegionCoverageSummary;
import cartographer.model.DisplayPosition;
import cartographer.model.HomeLocation;
import cartographer.model.HomeState;
import cartographer.model.WorldMetadata;
import cartographer.model.WorldPosition;
import cartographer.navigation.HomeStore;
import cartographer.save.ReadDiagnostics;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;

import java.nio.file.Path;
import java.util.Objects;

public final class RenderCoverageMapUseCase {
    private final VcdbsReader reader;
    private final WorldMetadataReader metadataReader;
    private final HomeStore homeStore;
    private final RegionCoverageAnalyzer analyzer;
    private final RegionCoverageRenderer renderer;

    public RenderCoverageMapUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            HomeStore homeStore,
            RegionCoverageAnalyzer analyzer,
            RegionCoverageRenderer renderer
    ) {
        this.reader = Objects.requireNonNull(reader, "reader is required");
        this.metadataReader = Objects.requireNonNull(metadataReader, "metadata reader is required");
        this.homeStore = Objects.requireNonNull(homeStore, "home store is required");
        this.analyzer = Objects.requireNonNull(analyzer, "coverage analyzer is required");
        this.renderer = Objects.requireNonNull(renderer, "coverage renderer is required");
    }

    public RenderCoverageMapResult execute(RenderCoverageMapRequest request) {
        return execute(request, ProgressReporter.NONE);
    }

    public RenderCoverageMapResult execute(
            RenderCoverageMapRequest request,
            ProgressReporter progress
    ) {
        Objects.requireNonNull(request, "coverage request is required");
        Objects.requireNonNull(progress, "progress is required");
        Path savePath = request.savePath();
        ReadDiagnostics diagnostics = new ReadDiagnostics();
        var regions = reader.readMapRegions(savePath, diagnostics, progress);
        WorldMetadata metadata = metadataReader.read(savePath, progress);
        RegionCoverageSummary summary = analyzer.analyze(regions, metadata);
        WorldPosition player = reader.readPlayerPosition(savePath, progress);
        HomeState home = absoluteHome(savePath, metadata);
        progress.start("Rendering coverage");
        var rendered = renderer.render(summary, player, home);
        progress.done("Coverage rendered");
        return new RenderCoverageMapResult(
                rendered.image(), rendered.geometry(), summary, diagnostics
        );
    }

    private HomeState absoluteHome(Path savePath, WorldMetadata metadata) {
        return homeStore.load(savePath)
                .map(location -> toAbsoluteHome(location, metadata))
                .map(HomeState::present)
                .orElseGet(HomeState::absent);
    }

    private HomeLocation toAbsoluteHome(HomeLocation displayHome, WorldMetadata metadata) {
        WorldPosition absolute = metadata.toAbsolute(
                new DisplayPosition(displayHome.x(), 0.0, displayHome.z())
        );
        return new HomeLocation(absolute.x(), absolute.z());
    }
}
