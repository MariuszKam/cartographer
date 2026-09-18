package cartographer.perf.fingerprint;

import cartographer.application.RenderActualOreMapResult;
import cartographer.scanner.SurfaceMap;

import java.util.Objects;

/** Canonical semantic fingerprint for the stable render-result contract. */
public final class RenderActualOreMapResultFingerprinter {
    private RenderActualOreMapResultFingerprinter() {
    }

    public static ResultFingerprint fingerprint(RenderActualOreMapResult result) {
        Objects.requireNonNull(result, "result is required");
        return SemanticFingerprinter.fingerprint(writer -> writeCanonical(writer, result));
    }

    private static void writeCanonical(
            CanonicalWriter writer,
            RenderActualOreMapResult result
    ) {
        var geometry = result.geometry();
        writer.writeInt(geometry.imageWidth())
                .writeInt(geometry.imageHeight())
                .writeInt(geometry.contentX())
                .writeInt(geometry.contentY())
                .writeInt(geometry.contentWidth())
                .writeInt(geometry.contentHeight())
                .writeLong(Double.doubleToLongBits(geometry.worldMinX()))
                .writeLong(Double.doubleToLongBits(geometry.worldMinZ()))
                .writeLong(Double.doubleToLongBits(geometry.worldMaxXExclusive()))
                .writeLong(Double.doubleToLongBits(geometry.worldMaxZExclusive()));

        writeSurface(writer, result.surface());
    }

    private static void writeSurface(
            CanonicalWriter writer,
            cartographer.scanner.SurfaceMapScanResult result
    ) {
        writer.writeInt(result.columnsScanned())
                .writeInt(result.emptyColumns())
                .writeInt(result.liquidUnavailableColumns());
        SurfaceMap map = result.map();
        final int[] count = {0};
        map.forEachCell((x, z, state, surfaceY, blockId, liquidBlockId, surfaceClass) -> count[0]++);
        writer.writeSequenceStart(count[0]);
        map.forEachCell((x, z, state, surfaceY, blockId, liquidBlockId, surfaceClass) ->
                writer.writeInt(x)
                        .writeInt(z)
                        .writeInt(state)
                        .writeInt(surfaceY)
                        .writeInt(blockId)
                        .writeInt(liquidBlockId)
                        .writeEnum(surfaceClass));
    }

}
