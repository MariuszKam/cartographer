package cartographer.scanner;

import java.util.StringJoiner;

/** Test-only deterministic fingerprint for compact Surface state. */
final class SurfaceSemanticOracle {
    private SurfaceSemanticOracle() {
    }

    static String fingerprint(SurfaceMap map) {
        StringJoiner result = new StringJoiner(";");
        map.forEachCell((x, z, state, y, blockId, liquidId, surfaceClass) ->
                result.add(x + "," + z + "," + state + "," + y + ","
                        + blockId + "," + liquidId + "," + surfaceClass));
        return result.toString();
    }
}
