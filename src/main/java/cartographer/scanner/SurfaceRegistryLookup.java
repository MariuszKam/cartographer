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
    private final String[] materialTypes;
    private final String[] rockFamilies;

    public SurfaceRegistryLookup(Map<Integer, BlockInfo> registry) {
        Objects.requireNonNull(registry, "registry is required");
        BlockInfo[] entries = registry.values().stream().filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(BlockInfo::id)).toArray(BlockInfo[]::new);
        ids = new int[entries.length]; air = new boolean[entries.length]; foliage = new boolean[entries.length];
        water = new boolean[entries.length]; classes = new SurfaceClass[entries.length];
        fertility = new SoilFertilityClassification[entries.length];
        codes = new String[entries.length]; materialTypes = new String[entries.length];
        rockFamilies = new String[entries.length];
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
            materialTypes[i] = info.materialType();
            rockFamilies[i] = rockFamily(codes[i]);
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

    public String materialType(int id) {
        int i = index(id);
        return i < 0 ? id == 0 ? "air" : "solid" : materialTypes[i];
    }

    public String rockFamily(int id) {
        int i = index(id);
        return i < 0 ? "unknown" : rockFamilies[i];
    }

    public int size() { return ids.length; }

    public int slot(int id) { return index(id); }

    public String codeAt(int slot) {
        Objects.checkIndex(slot, codes.length);
        return codes[slot] == null ? "unknown:" + ids[slot] : codes[slot];
    }

    public String materialTypeAt(int slot) { return materialTypes[slot]; }

    public String rockFamilyAt(int slot) { return rockFamilies[slot]; }

    private int index(int id) { return Arrays.binarySearch(ids, id); }

    private String rockFamily(String code) {
        if (code == null || code.isBlank()) return "unknown";
        String normalized = code.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("granite")) return "granite";
        if (normalized.contains("andesite")) return "andesite";
        if (normalized.contains("basalt")) return "basalt";
        if (normalized.contains("limestone")) return "limestone";
        if (normalized.contains("sandstone")) return "sandstone";
        if (normalized.contains("shale")) return "shale";
        if (normalized.contains("slate")) return "slate";
        if (normalized.contains("rock") || normalized.contains("stone")) return "rock";
        return "unknown";
    }
}
