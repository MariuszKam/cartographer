package cartographer.soil;

public enum SoilFertilityTier {
    BONY(0),
    BARREN(5),
    LOW(25),
    MEDIUM(50),
    HIGH(65),
    TERRA_PRETA(80);

    private final int fertilityPercent;

    SoilFertilityTier(int fertilityPercent) {
        this.fertilityPercent = fertilityPercent;
    }

    public int fertilityPercent() {
        return fertilityPercent;
    }
}
