package cartographer.application;

import java.awt.Color;
import cartographer.scanner.ActualBlockMatchMode;
import java.util.Objects;

public record ActualOreOverlaySpec(
        String displayName,
        String match,
        Color color,
        ActualBlockMatchMode matchMode
) {

    public ActualOreOverlaySpec(
            String displayName,
            String match,
            Color color
    ) {
        this(
                displayName,
                match,
                color,
                ActualBlockMatchMode.ORE_CODE
        );
    }

    public ActualOreOverlaySpec {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (match == null || match.isBlank()) {
            throw new IllegalArgumentException("match must not be blank");
        }
        Objects.requireNonNull(color, "color is required");
        Objects.requireNonNull(matchMode, "matchMode is required");
    }
}
