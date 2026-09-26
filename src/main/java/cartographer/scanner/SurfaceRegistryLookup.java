package cartographer.scanner;

import cartographer.model.BlockInfo;
import cartographer.model.SurfaceClass;
import cartographer.soil.SoilFertilityClassification;
import cartographer.soil.SoilFertilityClassifier;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/** Immutable registry-derived primitive lookup for Surface hot loops. */
public final class SurfaceRegistryLookup {
    private final int[] ids;
    private final boolean[] air;
    private final boolean[] foliage;
    private final boolean[] water;
    private final SurfaceClass[] classes;
    private final SoilFertilityClassification[] fertility;
    private final String[] codes;

    public SurfaceRegistryLookup(Map<Integer, BlockInfo> registry) {
        Objects.requireNonNull(registry, "registry is required");
        BlockInfo[] entries = registry.values().stream().filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(BlockInfo::id)).toArray(BlockInfo[]::new);
        ids = new int[entries.length]; air = new boolean[entries.length]; foliage = new boolean[entries.length];
        water = new boolean[entries.length]; classes = new SurfaceClass[entries.length];
        fertility = new SoilFertilityClassification[entries.length];
        codes = new String[entries.length];
        SurfaceClassifier classifier = new SurfaceClassifier();
        SoilFertilityClassifier fertilityClassifier = new SoilFertilityClassifier();
        BlockInfo unknownLiquid = BlockInfo.unknown(0);
        for (int i = 0; i < entries.length; i++) {
            BlockInfo info = entries[i]; ids[i] = info.id(); air[i] = info.isAir(); foliage[i] = info.isFoliage();
            water[i] = info.id() != 0 && info.code() != null
                    && info.code().toLowerCase(java.util.Locale.ROOT).contains("water");
            classes[i] = info.id() == 0
                    ? SurfaceClass.UNKNOWN : classifier.classify(info, unknownLiquid);
            fertility[i] = fertilityClassifier.classify(info).orElse(null);
            codes[i] = info.code();
        }
    }

    public boolean isAir(int id) { int i = index(id); return id == 0 || i >= 0 && air[i]; }
    public boolean isFoliage(int id) { int i = index(id); return i >= 0 && foliage[i]; }
    public boolean isWater(int id) { int i = index(id); return i >= 0 && water[i]; }
    public SurfaceClass classify(int blockId, int liquidId) {
        if (isWater(liquidId)) return SurfaceClass.WATER;
        int i = index(blockId);
        return i < 0 ? SurfaceClass.UNKNOWN : classes[i];
    }

    public SoilFertilityClassification fertility(int id) {
        int i = index(id);
        return i < 0 ? null : fertility[i];
    }

    public String code(int id) {
        int i = index(id);
        return i < 0 || codes[i] == null ? "unknown:" + id : codes[i];
    }

    public int slot(int id) { return index(id); }

    public String codeAt(int slot) {
        Objects.checkIndex(slot, codes.length);
        return codes[slot] == null ? "unknown:" + ids[slot] : codes[slot];
    }

    private int index(int id) { return Arrays.binarySearch(ids, id); }

}
