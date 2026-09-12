package cartographer.geology.crosssection;

import java.util.List;
import java.util.Objects;

public record GeologyCrossSection(
        int startWorldX,
        int startWorldZ,
        int endWorldX,
        int endWorldZ,
        int minYInclusive,
        int maxYExclusive,
        List<GeologySectionColumn> columns
) {

    public GeologyCrossSection {
        if (maxYExclusive < minYInclusive) {
            throw new IllegalArgumentException(
                    "GeologyCrossSection maxYExclusive must not be below minYInclusive"
            );
        }

        columns =
                List.copyOf(
                        Objects.requireNonNull(
                                columns,
                                "GeologyCrossSection columns are required"
                        )
                );

        if (columns.isEmpty()) {
            throw new IllegalArgumentException(
                    "GeologyCrossSection must contain at least one column"
            );
        }

        for (int index = 0;
             index < columns.size();
             index++) {

            GeologySectionColumn column =
                    columns.get(
                            index
                    );

            if (column.index() != index) {
                throw new IllegalArgumentException(
                        "GeologyCrossSection column indices must be sequential"
                );
            }

            validateVerticalCoverage(
                    column,
                    minYInclusive,
                    maxYExclusive
            );
        }

        GeologySectionColumn first =
                columns.getFirst();

        GeologySectionColumn last =
                columns.getLast();

        if (first.worldX() != startWorldX
                || first.worldZ() != startWorldZ) {

            throw new IllegalArgumentException(
                    "GeologyCrossSection first column must match start coordinates"
            );
        }

        if (last.worldX() != endWorldX
                || last.worldZ() != endWorldZ) {

            throw new IllegalArgumentException(
                    "GeologyCrossSection last column must match end coordinates"
            );
        }
    }

    private static void validateVerticalCoverage(
            GeologySectionColumn column,
            int minYInclusive,
            int maxYExclusive
    ) {
        if (minYInclusive == maxYExclusive) {
            if (!column.runs().isEmpty()) {
                throw new IllegalArgumentException(
                        "Empty vertical range must not contain geology runs"
                );
            }

            return;
        }

        if (column.runs().isEmpty()) {
            throw new IllegalArgumentException(
                    "Non-empty vertical range requires geology runs"
            );
        }

        GeologySectionRun first =
                column.runs()
                        .getFirst();

        GeologySectionRun last =
                column.runs()
                        .getLast();

        if (first.minYInclusive()
                != minYInclusive) {

            throw new IllegalArgumentException(
                    "GeologySectionColumn does not start at cross-section minimum Y"
            );
        }

        if (last.maxYExclusive()
                != maxYExclusive) {

            throw new IllegalArgumentException(
                    "GeologySectionColumn does not end at cross-section maximum Y"
            );
        }
    }
}
