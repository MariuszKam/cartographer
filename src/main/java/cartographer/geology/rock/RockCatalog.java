package cartographer.geology.rock;

import cartographer.model.BlockInfo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class RockCatalog {
    private final Map<Integer, RockIdentity> byBlockId;
    private final List<RockIdentity> rocks;
    private final List<Integer> rockBlockIds;

    private RockCatalog(Map<Integer, RockIdentity> byBlockId) {
        this.byBlockId = Map.copyOf(byBlockId);
        this.rocks = List.copyOf(byBlockId.values());
        this.rockBlockIds = List.copyOf(byBlockId.keySet());
    }

    public static RockCatalog from(Map<Integer, BlockInfo> blockRegistry) {
        Objects.requireNonNull(blockRegistry, "block registry is required");

        RockCodeResolver resolver = new RockCodeResolver();
        Map<Integer, RockIdentity> recognized = new TreeMap<>();
        for (Map.Entry<Integer, BlockInfo> entry : blockRegistry.entrySet()) {
            resolver.resolve(entry.getValue())
                    .ifPresent(identity -> recognized.put(entry.getKey(), identity));
        }
        return new RockCatalog(recognized);
    }

    public Optional<RockIdentity> findByBlockId(int blockId) {
        return Optional.ofNullable(byBlockId.get(blockId));
    }

    public List<RockIdentity> rocks() {
        return rocks;
    }

    public List<Integer> rockBlockIds() {
        return rockBlockIds;
    }
}
