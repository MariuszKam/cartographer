package cartographer.render;

public record GeologySectionMarker(
        String label,
        int columnIndex,
        int worldY
) {

    public GeologySectionMarker {
        if (columnIndex < 0) {
            throw new IllegalArgumentException(
                    "GeologySectionMarker columnIndex must be non-negative"
            );
        }

        if (label == null
                || label.isBlank()) {
            label =
                    "MARKER";
        }
    }
}
