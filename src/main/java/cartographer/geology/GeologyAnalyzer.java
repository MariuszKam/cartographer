package cartographer.geology;

import cartographer.model.SurfaceBlock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GeologyAnalyzer {
    public GeologyReport analyze(List<SurfaceBlock> surfaceBlocks) {
        Map<String, Integer> rockFamilies = new LinkedHashMap<>();
        Map<String, Integer> materialTypes = new LinkedHashMap<>();
        int geologicalSamples = 0;
        int unknownSamples = 0;

        for (SurfaceBlock block : surfaceBlocks) {
            String material = block.blockInfo().materialType();
            materialTypes.merge(material, 1, Integer::sum);
            String rockFamily = rockFamily(block.blockInfo().code());
            if ("unknown".equals(rockFamily)) {
                unknownSamples++;
            }
            if (isGeological(material, rockFamily)) {
                geologicalSamples++;
                rockFamilies.merge(rockFamily, 1, Integer::sum);
            }
        }

        return new GeologyReport(
                surfaceBlocks.size(),
                geologicalSamples,
                unknownSamples,
                Map.copyOf(rockFamilies),
                Map.copyOf(materialTypes));
    }

    private boolean isGeological(String material, String rockFamily) {
        return "rock".equals(material) || "ground".equals(material) || !"unknown".equals(rockFamily);
    }

    private String rockFamily(String code) {
        if (code == null || code.isBlank()) {
            return "unknown";
        }
        String normalized = code.toLowerCase(Locale.ROOT);
        if (normalized.contains("granite")) {
            return "granite";
        }
        if (normalized.contains("andesite")) {
            return "andesite";
        }
        if (normalized.contains("basalt")) {
            return "basalt";
        }
        if (normalized.contains("limestone")) {
            return "limestone";
        }
        if (normalized.contains("sandstone")) {
            return "sandstone";
        }
        if (normalized.contains("shale")) {
            return "shale";
        }
        if (normalized.contains("slate")) {
            return "slate";
        }
        if (normalized.contains("rock") || normalized.contains("stone")) {
            return "rock";
        }
        return "unknown";
    }
}
