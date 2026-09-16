package cartographer.perf.workload;

public enum RadiusProfile {
    R128(128),
    R256(256),
    R512(512),
    R1024(1024),
    R2048(2048),
    R4096(4096);

    private final int blocks;

    RadiusProfile(int blocks) {
        this.blocks = blocks;
    }

    public int blocks() {
        return blocks;
    }
}
