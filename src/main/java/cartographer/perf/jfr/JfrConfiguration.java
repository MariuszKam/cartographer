package cartographer.perf.jfr;

public enum JfrConfiguration {
    PROFILE("profile");

    private final String jfrName;

    JfrConfiguration(String jfrName) {
        this.jfrName = jfrName;
    }

    String jfrName() {
        return jfrName;
    }
}
