package cartographer.geology;

import cartographer.scanner.SurfaceMapScanResult;
import cartographer.scanner.SurfaceRegistryLookup;

import java.util.LinkedHashMap;
import java.util.Map;

public class GeologyAnalyzer {
    public GeologyReport analyze(SurfaceMapScanResult surface) {
        if (surface == null) {
            throw new IllegalArgumentException("surface result is required");
        }

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

            if ("unknown".equals(family)) {
                unknownSamples[0]++;
            }
            if (isGeological(material, family)) {
                geologicalSamples[0]++;
                if (slot >= 0) {
                    rockFamilyCounts[slot]++;
                }
            }
        });

        for (int slot = 0; slot < lookup.size(); slot++) {
            if (materialCounts[slot] != 0) {
                materialTypes.merge(
                        lookup.materialTypeAt(slot),
                        materialCounts[slot],
                        Integer::sum
                );
            }
            if (rockFamilyCounts[slot] != 0) {
                rockFamilies.merge(
                        lookup.rockFamilyAt(slot),
                        rockFamilyCounts[slot],
                        Integer::sum
                );
            }
        }

        if (missingAirCount[0] != 0) {
            materialTypes.merge("air", missingAirCount[0], Integer::sum);
        }
        if (missingSolidCount[0] != 0) {
            materialTypes.merge("solid", missingSolidCount[0], Integer::sum);
        }

        return new GeologyReport(
                samples[0],
                geologicalSamples[0],
                unknownSamples[0],
                Map.copyOf(rockFamilies),
                Map.copyOf(materialTypes)
        );
    }

    private boolean isGeological(String material, String rockFamily) {
        return "rock".equals(material)
                || "ground".equals(material)
                || !"unknown".equals(rockFamily);
    }
}
