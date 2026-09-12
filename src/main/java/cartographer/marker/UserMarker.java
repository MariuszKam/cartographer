package cartographer.marker;

/*
 * User marker coordinates are ALWAYS stored in Vintage Story
 * display-coordinate space.
 *
 * Example for a 1,024,000 wide world:
 *
 * display X  = absolute X - 512000
 * display Z  = absolute Z - 512000
 *
 * Conversion to absolute world coordinates belongs to the
 * rendering/navigation boundary, not to MarkerStore.
 */
public record UserMarker(
        String name,
        double x,
        double z
) {

    public UserMarker {
        if (name == null
                || name.isBlank()) {

            throw new IllegalArgumentException(
                    "Marker name is required"
            );
        }

        name =
                name.trim();

        if (!Double.isFinite(x)
                || !Double.isFinite(z)) {

            throw new IllegalArgumentException(
                    "Marker coordinates must be finite"
            );
        }
    }
}