package cartographer.application;

import cartographer.model.WorldPosition;
import cartographer.render.RenderLayer;
import cartographer.render.RenderStyle;
import cartographer.scanner.ActualBlockYFilter;
import cartographer.scanner.ActualBlockMatchMode;

import java.nio.file.Path;
import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record RenderActualOreMapRequest(
        Path savePath,
        int radius,
        int pixelsPerBlock,
        RenderStyle style,
        Set<RenderLayer> layers,
        Optional<String> oreMatch,
        ActualBlockYFilter yFilter,
        Optional<WorldPosition> center,
        List<ActualOreOverlaySpec> oreOverlays
) {

    public RenderActualOreMapRequest(
            Path savePath,
            int radius,
            int pixelsPerBlock,
            RenderStyle style,
            Set<RenderLayer> layers,
            Optional<String> oreMatch,
            ActualBlockYFilter yFilter,
            Optional<WorldPosition> center
    ) {
        this(
                savePath,
                radius,
                pixelsPerBlock,
                style,
                layers,
                oreMatch,
                yFilter,
                center,
                defaultOverlays(oreMatch)
        );
    }

    public RenderActualOreMapRequest {
        if (savePath == null) {
            throw new NullPointerException("savePath is required");
        }
        if (radius <= 0) {
            throw new IllegalArgumentException("radius must be positive");
        }
        if (pixelsPerBlock <= 0) {
            throw new IllegalArgumentException("pixelsPerBlock must be positive");
        }
        if (style == null) {
            throw new NullPointerException("style is required");
        }
        layers = Set.copyOf(layers);
        oreMatch = Objects.requireNonNull(
                oreMatch,
                "oreMatch is required; use Optional.empty() when absent"
        );
        yFilter = yFilter == null ? ActualBlockYFilter.unbounded() : yFilter;
        center = Objects.requireNonNull(
                center,
                "center is required; use Optional.empty() when absent"
        );
        oreOverlays = List.copyOf(
                oreOverlays == null ? List.of() : oreOverlays
        );

        if (oreMatch.isPresent() && oreMatch.orElseThrow().isBlank()) {
            throw new IllegalArgumentException("oreMatch must not be blank");
        }
    }

    private static List<ActualOreOverlaySpec> defaultOverlays(
            Optional<String> oreMatch
    ) {
        Objects.requireNonNull(
                oreMatch,
                "oreMatch is required; use Optional.empty() when absent"
        );
        if (oreMatch.isEmpty()) {
            return List.of();
        }
        String match = oreMatch.orElseThrow();
        return List.of(
                new ActualOreOverlaySpec(
                        match,
                        match,
                        new Color(225, 92, 24),
                        ActualBlockMatchMode.GENERIC_SUBSTRING
                )
        );
    }
}
