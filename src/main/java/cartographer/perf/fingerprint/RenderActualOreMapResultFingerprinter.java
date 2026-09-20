package cartographer.perf.fingerprint;

import cartographer.application.RenderActualOreMapResult;
import cartographer.scanner.SurfaceDiagnosticsSummary;

import java.util.ArrayList;
import java.util.Objects;

/** Canonical semantic fingerprint for the stable render-result contract. */
public final class RenderActualOreMapResultFingerprinter {
    private RenderActualOreMapResultFingerprinter() {
    }

    public static ResultFingerprint fingerprint(
            RenderActualOreMapResult result
    ) {
        Objects.requireNonNull(result, "result is required");
        return SemanticFingerprinter.fingerprint(
                writer -> writeCanonical(writer, result)
        );
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
                .writeLong(Double.doubleToLongBits(
                        geometry.worldMaxXExclusive()
                ))
                .writeLong(Double.doubleToLongBits(
                        geometry.worldMaxZExclusive()
                ));

        writeSurface(writer, result.surface());
    }

    private static void writeSurface(
            CanonicalWriter writer,
            SurfaceDiagnosticsSummary result
    ) {
        writer.writeInt(result.columnsScanned())
                .writeInt(result.emptyColumns())
                .writeInt(result.liquidUnavailableColumns())
                .writeLong(result.waterColumns())
                .writeLong(result.unknownSurfaceBlocks());

        var unknown = result.topUnknownSurfaceBlockCodes(
                Integer.MAX_VALUE
        );
        writer.writeSequenceStart(unknown.size());
        for (var value : unknown) {
            writer.writeString(value.code())
                    .writeLong(value.count());
        }

        var distinct = new ArrayList<>(
                result.distinctSurfaceBlockCodes(Integer.MAX_VALUE)
        );
        distinct.sort(String::compareTo);
        writer.writeSequenceStart(distinct.size());
        for (String code : distinct) {
            writer.writeString(code);
        }
    }
}
