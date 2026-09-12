package cartographer.model;

import java.util.Objects;

public sealed interface HomeState
        permits HomeState.Present,
        HomeState.Absent {

    static HomeState present(
            HomeLocation location
    ) {
        return new Present(
                location
        );
    }

    static HomeState absent() {
        return Absent.INSTANCE;
    }

    record Present(
            HomeLocation location
    ) implements HomeState {

        public Present {
            Objects.requireNonNull(
                    location,
                    "Home location is required"
            );
        }
    }

    enum Absent
            implements HomeState {

        INSTANCE
    }
}