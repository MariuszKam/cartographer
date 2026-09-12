package cartographer.geology.crosssection;

import java.util.List;
import java.util.Objects;

public record GeologySectionColumn(
        int index,
        int worldX,
        int worldZ,
        List<GeologySectionRun> runs
) {

    public GeologySectionColumn {
        if (index < 0) {
            throw new IllegalArgumentException(
                    "GeologySectionColumn index must be non-negative"
            );
        }

        runs =
                List.copyOf(
                        Objects.requireNonNull(
                                runs,
                                "GeologySectionColumn runs are required"
                        )
                );

        for (int runIndex = 1;
             runIndex < runs.size();
             runIndex++) {

            GeologySectionRun previous =
                    runs.get(
                            runIndex - 1
                    );

            GeologySectionRun current =
                    runs.get(
                            runIndex
                    );

            if (previous.maxYExclusive()
                    != current.minYInclusive()) {

                throw new IllegalArgumentException(
                        "GeologySectionColumn runs must be contiguous"
                );
            }
        }
    }
}
