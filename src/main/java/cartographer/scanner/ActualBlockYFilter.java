package cartographer.scanner;

public record ActualBlockYFilter(
        Integer minInclusive,
        Integer maxInclusive
) {

    public ActualBlockYFilter {
        if (minInclusive != null
                && maxInclusive != null
                && minInclusive > maxInclusive) {

            throw new IllegalArgumentException(
                    "minInclusive must not be greater than maxInclusive"
            );
        }
    }

    public static ActualBlockYFilter unbounded() {
        return new ActualBlockYFilter(
                null,
                null
        );
    }

    public boolean enabled() {
        return minInclusive != null
                || maxInclusive != null;
    }

    public boolean includes(
            int worldY
    ) {
        if (minInclusive != null
                && worldY < minInclusive) {
            return false;
        }

        return maxInclusive == null
                || worldY <= maxInclusive;
    }

    public String description() {
        if (minInclusive == null
                && maxInclusive == null) {
            return "all";
        }

        if (minInclusive == null) {
            return "<="
                    + maxInclusive;
        }

        if (maxInclusive == null) {
            return ">="
                    + minInclusive;
        }

        return minInclusive
                + ".."
                + maxInclusive;
    }
}
