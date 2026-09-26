package cartographer.prospecting;

import cartographer.model.BlockInfo;
import cartographer.scanner.OreCodeMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Immutable sparse ID-to-resource-membership table. No registry work occurs in the block loop. */
final class CompiledProspectingClassifier {
    private final int[] ids;
    private final int[] offsets;
    private final int[] memberships;

    private CompiledProspectingClassifier(int[] ids, int[] offsets, int[] memberships) {
        this.ids = ids;
        this.offsets = offsets;
        this.memberships = memberships;
    }

    static CompiledProspectingClassifier compile(
            Map<Integer, BlockInfo> registry,
            List<String> resources,
            List<Integer> rockIds
    ) {
        List<Integer> union = new ArrayList<>(rockIds);
        for (BlockInfo block : registry.values()) {
            if (block == null || block.code() == null) continue;
            for (String resource : resources) {
                if (OreCodeMatcher.matchesOreCode(block.code(), resource)) {
                    union.add(block.id());
                    break;
                }
            }
        }
        union.sort(Integer::compareTo);
        int unique = 0;
        for (int id : union) if (unique == 0 || union.get(unique - 1) != id) union.set(unique++, id);
        int[] ids = new int[unique];
        int[] offsets = new int[unique + 1];
        int membershipCount = 0;
        for (int i = 0; i < unique; i++) {
            int id = union.get(i);
            ids[i] = id;
            offsets[i] = membershipCount;
            BlockInfo block = registry.get(id);
            if (block != null && block.code() != null) {
                for (String resource : resources) {
                    if (OreCodeMatcher.matchesOreCode(block.code(), resource)) membershipCount++;
                }
            }
        }
        offsets[unique] = membershipCount;
        int[] memberships = new int[membershipCount];
        int cursor = 0;
        for (int i = 0; i < unique; i++) {
            BlockInfo block = registry.get(ids[i]);
            if (block == null || block.code() == null) continue;
            for (int resource = 0; resource < resources.size(); resource++) {
                if (OreCodeMatcher.matchesOreCode(block.code(), resources.get(resource))) memberships[cursor++] = resource;
            }
        }
        return new CompiledProspectingClassifier(ids, offsets, memberships);
    }

    int[] interestingIds() { return ids.clone(); }
    int start(int blockId) { int i = Arrays.binarySearch(ids, blockId); return i < 0 ? -1 : offsets[i]; }
    int end(int blockId) { int i = Arrays.binarySearch(ids, blockId); return i < 0 ? -1 : offsets[i + 1]; }
    int membershipAt(int index) { return memberships[index]; }
}
