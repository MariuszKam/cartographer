package cartographer.scanner;

public record ActualBlockMapCell(
        int worldX,
        int worldZ,
        int matchCount,
        int minY,
        int maxY
) {

    public ActualBlockMapCell {
        if (matchCount <= 0) {
            throw new IllegalArgumentException(
                    "ActualBlockMapCell matchCount must be positive"
            );
        }

        if (maxY < minY) {
            throw new IllegalArgumentException(
                    "ActualBlockMapCell maxY must be greater than or equal to minY"
            );
        }
    }
}
