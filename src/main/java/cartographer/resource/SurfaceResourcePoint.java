package cartographer.resource;

public record SurfaceResourcePoint(
        int worldX,
        int y,
        int worldZ,
        String blockCode
) {
    public SurfaceResourcePoint {
        if (blockCode == null
                || blockCode.isBlank()) {

            throw new IllegalArgumentException(
                    "Surface resource block code is required"
            );
        }
    }
}