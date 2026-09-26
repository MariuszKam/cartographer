package cartographer.ui;

import cartographer.application.AnalyzeProspectingAreaUseCase;
import cartographer.application.DiscoverObservedSurfaceResourcesUseCase;
import cartographer.application.LoadWorldOverviewUseCase;
import cartographer.application.InspectWorldSnapshotStatusUseCase;
import cartographer.application.PrepareWorldSnapshotUseCase;
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
import cartographer.cache.RenderDataCacheStore;
import cartographer.prospecting.OreRockCompatibilityProvider;
import cartographer.prospecting.SavedOreObservationProvider;
import cartographer.render.ActualOreOverlayPainter;
import cartographer.render.MapRenderer;
import cartographer.render.RockMapRenderer;
import cartographer.render.SurfaceResourceOverlayRenderer;
import cartographer.render.UserMarkerRenderer;
import cartographer.resource.ResourceAnalyzer;
import cartographer.resource.SurfaceMaterialAnalyzer;
import cartographer.save.SaveSessionFactory;
import cartographer.save.SqliteSaveConnection;
import cartographer.save.VcdbsReader;
import cartographer.save.WorldMetadataReader;
import cartographer.ui.update.DesktopUpdateController;
import cartographer.update.ApplicationVersion;
import cartographer.update.HttpUpdateInstallerSource;
import cartographer.update.HttpUpdateManifestSource;
import cartographer.update.UpdateCheckService;
import cartographer.update.UpdateDownloadService;
import cartographer.update.UpdateEndpoints;
import cartographer.update.UpdateInstallOutcomeStore;
import cartographer.update.UpdateInstallerVerifier;
import cartographer.update.UpdateInstallService;
import cartographer.update.UpdateManifestParser;
import cartographer.update.UpdatePreferencesStore;
import cartographer.update.WindowsUpdateBootstrapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CartographerDesktopApp extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(CartographerDesktopApp.class);

    private ExecutorService updateExecutor;
    private WorkstationController workstationController;
    private HttpUpdateManifestSource updateManifestSource;
    private HttpUpdateInstallerSource updateInstallerSource;

    @Override
    public void start(Stage stage) {
        LOGGER.info("Initializing desktop application");
        VcdbsReader reader = createReader();
        WorldMetadataReader metadataReader = new WorldMetadataReader();
        SaveSessionFactory sessionFactory = new SaveSessionFactory(
                new SqliteSaveConnection(),
                reader,
                metadataReader
        );
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
                sessionFactory,
                renderDataCacheStore,
                config
        );
        RenderCoverageMapUseCase coverageUseCase = createCoverageUseCase(
                reader,
                sessionFactory,
                config
        );
        RenderSurfaceResourceMapUseCase surfaceUseCase =
                createSurfaceUseCase(
                        reader,
                        sessionFactory,
                        renderDataCacheStore,
                        config
                );
        DiscoverObservedSurfaceResourcesUseCase surfaceDiscoveryUseCase =
                new DiscoverObservedSurfaceResourcesUseCase(reader, sessionFactory);
        RenderRockMapUseCase rockUseCase =
                new RenderRockMapUseCase(
                        reader,
                        new RockMapRenderer(),
                        sessionFactory,
                        renderDataCacheStore
                );
        PrepareWorldSnapshotUseCase prepareWorldSnapshotUseCase =
                new PrepareWorldSnapshotUseCase(
                        reader,
                        sessionFactory,
                        renderDataCacheStore
                );
        InspectWorldSnapshotStatusUseCase snapshotStatusUseCase =
                new InspectWorldSnapshotStatusUseCase(
                        renderDataCacheStore
                );

        AnalyzeProspectingAreaUseCase prospectingUseCase =
                new AnalyzeProspectingAreaUseCase(
                        reader,
                        new ResourceAnalyzer(),
                        OreRockCompatibilityProvider.unknown(),
                        new SavedOreObservationProvider(
                                reader,
                                sessionFactory,
                                renderDataCacheStore
                        ),
                        sessionFactory,
                        renderDataCacheStore
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
                        new ResourceAnalyzer(),
                        sessionFactory
                ),
                prepareWorldSnapshotUseCase,
                snapshotStatusUseCase
        );

        workstationController = controller;

        stage.setTitle("VS Cartographer");
        Scene scene = new Scene(controller.root(), 1440, 880);
        URL stylesheet = Objects.requireNonNull(
                getClass().getResource("/cartographer/ui/cartographer-dark.css"),
                "cartographer stylesheet is required"
        );
        scene.getStylesheets().add(stylesheet.toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        stage.show();

        startUpdateDetection(controller, config);
        LOGGER.info("Desktop application started");
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping desktop application");
        if (workstationController != null) {
            workstationController.shutdown();
        }
        if (updateExecutor != null) {
            updateExecutor.shutdownNow();
        }
        if (updateManifestSource != null) {
            updateManifestSource.close();
        }
        if (updateInstallerSource != null) {
            updateInstallerSource.close();
        }
        LOGGER.info("Desktop application stopped");
    }

    private void startUpdateDetection(
            WorkstationController controller,
            Path config
    ) {
        ApplicationVersion currentVersion = ApplicationVersion.current();
        updateManifestSource = new HttpUpdateManifestSource(
                UpdateEndpoints.latestStableManifest(),
                Duration.ofSeconds(3),
                Duration.ofSeconds(5)
        );
        UpdateCheckService updateCheckService = new UpdateCheckService(
                currentVersion,
                updateManifestSource,
                new UpdateManifestParser()
        );

        updateExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "cartographer-update-worker"
            );
            thread.setDaemon(true);
            return thread;
        });

        Path updatesRoot = config.resolve("updates");
        UpdateInstallerVerifier verifier = new UpdateInstallerVerifier();
        WindowsUpdateBootstrapper bootstrapper =
                new WindowsUpdateBootstrapper(updatesRoot);
        UpdateInstallOutcomeStore installOutcomeStore =
                new UpdateInstallOutcomeStore(
                        bootstrapper.outcomePath()
                );

        updateInstallerSource = new HttpUpdateInstallerSource(
                Duration.ofSeconds(5),
                Duration.ofMinutes(30)
        );
        DesktopUpdateController updateController =
                new DesktopUpdateController(
                        updateCheckService,
                        new UpdateDownloadService(
                                updatesRoot,
                                updateInstallerSource,
                                verifier
                        ),
                        new UpdateInstallService(
                                verifier,
                                bootstrapper
                        ),
                        installOutcomeStore::consume,
                        new UpdatePreferencesStore(
                                config.resolve("update.properties")
                        ),
                        controller.updateCheckView(),
                        updateExecutor,
                        Platform::runLater,
                        uri -> getHostServices().showDocument(
                                uri.toString()
                        ),
                        Platform::exit,
                        Clock.systemUTC(),
                        DesktopUpdateController.DEFAULT_AUTOMATIC_CHECK_INTERVAL
                );
        updateController.showPreviousInstallOutcome();
        updateController.startAutomaticCheck();
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
            SaveSessionFactory sessionFactory,
            RenderDataCacheStore renderDataCacheStore,
            Path config
    ) {
        return new RenderActualOreMapUseCase(
                reader,
                new HomeStore(config.resolve("home.properties")),
                new MarkerStore(config),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new ActualOreOverlayPainter(),
                sessionFactory,
                renderDataCacheStore
        );
    }

    private RenderSurfaceResourceMapUseCase createSurfaceUseCase(
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            RenderDataCacheStore renderDataCacheStore,
            Path config
    ) {
        return new RenderSurfaceResourceMapUseCase(
                reader,
                sessionFactory,
                new HomeStore(config.resolve("home.properties")),
                new MarkerStore(config),
                new MapRenderer(),
                new UserMarkerRenderer(),
                new SurfaceMaterialAnalyzer(),
                new SurfaceResourceOverlayRenderer(),
                renderDataCacheStore
        );
    }

    private RenderCoverageMapUseCase createCoverageUseCase(
            VcdbsReader reader,
            SaveSessionFactory sessionFactory,
            Path config
    ) {
        return new RenderCoverageMapUseCase(
                reader,
                sessionFactory,
                new HomeStore(config.resolve("home.properties")),
                new RegionCoverageAnalyzer(),
                new RegionCoverageRenderer()
        );
    }
}
