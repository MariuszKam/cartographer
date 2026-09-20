package cartographer.ui;

import cartographer.application.AnalyzeProspectingAreaUseCase;
import cartographer.application.DiscoverObservedSurfaceResourcesUseCase;
import cartographer.application.LoadWorldOverviewUseCase;
import cartographer.application.RenderActualOreMapUseCase;
import cartographer.application.RenderCoverageMapUseCase;
import cartographer.application.RenderRockMapUseCase;
import cartographer.application.RenderSurfaceResourceMapUseCase;
import cartographer.coverage.RegionCoverageAnalyzer;
import cartographer.coverage.RegionCoverageRenderer;
import cartographer.marker.MarkerStore;
import cartographer.navigation.HomeStore;
import cartographer.parser.ChunkParser;
import cartographer.parser.MapChunkParser;
import cartographer.parser.PlayerDataParser;
import cartographer.parser.RegistryParser;
import cartographer.perf.RenderDataCacheStore;
import cartographer.prospecting.OreRockCompatibilityProvider;
import cartographer.prospecting.SavedOreObservationProvider;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.RockMapRenderer;
import cartographer.render.SurfaceResourceOverlayRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.SurfaceMaterialAnalyzer;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.scanner.ActualBlockMapScanner;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class CartographerDesktopApp extends Application {

    @Override
    public void start(Stage stage) {
        VcdbsReader reader = createReader();
        WorldMetadataReader metadataReader = new WorldMetadataReader();
        Path config = Path.of(
                System.getProperty("user.home"),
                ".vs-cartographer"
        );
        RenderDataCacheStore renderDataCacheStore =
                new RenderDataCacheStore(
                        config.resolve("cache").resolve("render-data")
                );

        RenderActualOreMapUseCase mapUseCase = createUseCase(
                reader,
                metadataReader,
                renderDataCacheStore
        );
        RenderCoverageMapUseCase coverageUseCase = createCoverageUseCase(reader, metadataReader);
        RenderSurfaceResourceMapUseCase surfaceUseCase =
                createSurfaceUseCase(
                        reader,
                        metadataReader,
                        renderDataCacheStore
                );
        DiscoverObservedSurfaceResourcesUseCase surfaceDiscoveryUseCase =
                new DiscoverObservedSurfaceResourcesUseCase(reader, metadataReader);
        RenderRockMapUseCase rockUseCase =
                new RenderRockMapUseCase(
                        reader,
                        metadataReader,
                        new RockMapRenderer(),
                        renderDataCacheStore
                );
        AnalyzeProspectingAreaUseCase prospectingUseCase =
                new AnalyzeProspectingAreaUseCase(
                        reader,
                        rockUseCase,
                        new ResourceAnalyzer(),
                        OreRockCompatibilityProvider.unknown(),
                        new SavedOreObservationProvider(
                                reader,
                                metadataReader,
                                renderDataCacheStore
                        )
                );

        WorkstationController controller = new WorkstationController(
                () -> chooseSave(stage),
                mapUseCase,
                coverageUseCase,
                surfaceUseCase,
                surfaceDiscoveryUseCase,
                rockUseCase,
                prospectingUseCase,
                new LoadWorldOverviewUseCase(
                        reader,
                        metadataReader,
                        new ResourceAnalyzer()
                )
        );

        stage.setTitle("VS Cartographer");
        Scene scene = new Scene(controller.root(), 1440, 880);
        scene.getStylesheets().add(
                getClass().getResource("/cartographer/ui/cartographer-dark.css").toExternalForm()
        );
        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        stage.show();
    }

    private Optional<Path> chooseSave(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Vintage Story save");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Vintage Story saves (*.vcdbs)", "*.vcdbs")
        );
        Path saves = Path.of(
                System.getenv().getOrDefault("APPDATA", ""),
                "VintagestoryData",
                "Saves"
        );
        if (Files.isDirectory(saves)) {
            chooser.setInitialDirectory(saves.toFile());
        }
        return Optional.ofNullable(chooser.showOpenDialog(stage))
                .map(java.io.File::toPath);
    }

    private VcdbsReader createReader() {
        return new VcdbsReader(
                new PlayerDataParser(),
                new MapChunkParser(),
                new ChunkParser(),
                new RegistryParser()
        );
    }

    private RenderActualOreMapUseCase createUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            RenderDataCacheStore renderDataCacheStore
    ) {
        Path config = Path.of(System.getProperty("user.home"), ".vs-cartographer");
        return new RenderActualOreMapUseCase(
                reader,
                metadataReader,
                new HomeStore(config.resolve("home.properties")),
                new MarkerStore(config.resolve("markers.csv")),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new ActualBlockMapScanner(),
                new ActualOreOverlayPainter(),
                renderDataCacheStore
        );
    }

    private RenderSurfaceResourceMapUseCase createSurfaceUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader,
            RenderDataCacheStore renderDataCacheStore
    ) {
        Path config = Path.of(System.getProperty("user.home"), ".vs-cartographer");
        return new RenderSurfaceResourceMapUseCase(
                reader,
                metadataReader,
                new HomeStore(config.resolve("home.properties")),
                new MarkerStore(config.resolve("markers.csv")),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new SurfaceMaterialAnalyzer(),
                new SurfaceResourceOverlayRenderer(),
                renderDataCacheStore
        );
    }

    private RenderCoverageMapUseCase createCoverageUseCase(
            VcdbsReader reader,
            WorldMetadataReader metadataReader
    ) {
        Path config = Path.of(System.getProperty("user.home"), ".vs-cartographer");
        return new RenderCoverageMapUseCase(
                reader,
                metadataReader,
                new HomeStore(config.resolve("home.properties")),
                new RegionCoverageAnalyzer(),
                new RegionCoverageRenderer()
        );
    }
}
