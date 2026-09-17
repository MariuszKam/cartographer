package cartographer.geology;

import cartographer.model.SurfaceBlock;
import cartographer.model.BlockInfo;
import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceRegistryLookup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GeologyAnalyzer {
    public GeologyReport analyze(SurfaceMapScanResult surface) {
        if (surface == null) throw new IllegalArgumentException("surface result is required");
        Map<String, Integer> rockFamilies = new LinkedHashMap<>();
        Map<String, Integer> materialTypes = new LinkedHashMap<>();
        int[] geologicalSamples = {0};
        int[] unknownSamples = {0};
        int[] samples = {0};
        SurfaceRegistryLookup lookup = new SurfaceRegistryLookup(surface.registry());
        int[] materialCounts = new int[lookup.size()];
        int[] rockFamilyCounts = new int[lookup.size()];
        int[] missingAirCount = {0};
        int[] missingSolidCount = {0};
        surface.map().forEachResolvedCell((x, z, y, blockId, liquidId, surfaceClass) -> {
            samples[0]++;
            int slot = lookup.slot(blockId);
            String material;
            String family;
            if (slot < 0) {
                material = lookup.materialType(blockId);
                family = "unknown";
                if (blockId == 0) {
                    missingAirCount[0]++;
                } else {
                    missingSolidCount[0]++;
                }
            } else {
                material = lookup.materialTypeAt(slot);
                family = lookup.rockFamilyAt(slot);
                materialCounts[slot]++;
            }
            if ("unknown".equals(family)) unknownSamples[0]++;
            if (isGeological(material, family)) {
                geologicalSamples[0]++;
                if (slot >= 0) rockFamilyCounts[slot]++;
            }
        });
        for (int slot = 0; slot < lookup.size(); slot++) {
            if (materialCounts[slot] != 0) {
                materialTypes.merge(lookup.materialTypeAt(slot), materialCounts[slot], Integer::sum);
            }
            if (rockFamilyCounts[slot] != 0) {
                rockFamilies.merge(lookup.rockFamilyAt(slot), rockFamilyCounts[slot], Integer::sum);
            }
        }
        if (missingAirCount[0] != 0) {
            materialTypes.merge("air", missingAirCount[0], Integer::sum);
        }
        if (missingSolidCount[0] != 0) {
            materialTypes.merge("solid", missingSolidCount[0], Integer::sum);
        }
        return new GeologyReport(samples[0], geologicalSamples[0], unknownSamples[0],
                Map.copyOf(rockFamilies), Map.copyOf(materialTypes));
    }

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
