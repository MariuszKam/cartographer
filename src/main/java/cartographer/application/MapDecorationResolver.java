package cartographer.application;

import cartographer.marker.MarkerStore;
import cartographer.model.DisplayPosition;
import cartographer.model.HomeState;
import cartographer.model.WorldMetadata;
import cartographer.navigation.HomeStore;
import cartographer.render.RenderLayer;
import cartographer.render.RenderOptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves HOME and user-marker decoration state for one save. */
final class MapDecorationResolver {
    private final HomeStore homeStore;
    private final MarkerStore markerStore;

    MapDecorationResolver(
            HomeStore homeStore,
            MarkerStore markerStore
    ) {
        this.homeStore = Objects.requireNonNull(
                homeStore,
                "homeStore is required"
        );
        this.markerStore = Objects.requireNonNull(
                markerStore,
                "markerStore is required"
        );
    }

    MapDecorationState resolve(
            Path savePath,
            WorldMetadata metadata,
            RenderOptions options
    ) {
        HomeState home = absoluteHome(savePath, metadata);
        try {
            return new MapDecorationState(
                    home,
                    markerStore.load(savePath),
                    true
            );
        } catch (RuntimeException exception) {
            if (options.layers().contains(RenderLayer.MARKERS)) {
                throw exception;
            }
            return new MapDecorationState(home, List.of(), false);
        }
    }

    private HomeState absoluteHome(
            Path savePath,
            WorldMetadata metadata
    ) {
        Optional<DisplayPosition> displayHome = homeStore.load(savePath);
        return displayHome
                .map(metadata::toAbsolute)
                .map(HomeState::present)
                .orElseGet(HomeState::absent);
    }
}
