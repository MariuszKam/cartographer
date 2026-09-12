package cartographer.application;

import cartographer.scanner.ActualBlockMap;

import java.util.Objects;

public record ActualOreOverlayResult(
        ActualOreOverlaySpec spec,
        ActualBlockMap map
) {

    public ActualOreOverlayResult {
        Objects.requireNonNull(spec, "spec is required");
        Objects.requireNonNull(map, "map is required");
    }
}
