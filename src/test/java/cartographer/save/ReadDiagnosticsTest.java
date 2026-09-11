package cartographer.save;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadDiagnosticsTest {
    @Test
    void aggregatesFailureReasonsSeparatelyFromSkippedSamples() {
        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        for (int index = 0; index < 50; index++) {
            diagnostics.recordSkipped(
                    "chunk outside requested radius"
            );
        }

        diagnostics.recordFailed(
                "blocksCompressed: zstd decompression failed"
        );

        diagnostics.recordFailed(
                "blocksCompressed: zstd decompression failed"
        );

        diagnostics.recordFailed(
                "invalid ServerChunk protobuf: Unexpected end of protobuf varint"
        );

        assertEquals(
                50,
                diagnostics.skipped()
        );

        assertEquals(
                3,
                diagnostics.failed()
        );

        assertEquals(
                2,
                diagnostics.failureReasons()
                        .get("blocksCompressed: zstd decompression failed")
        );

        assertTrue(
                diagnostics.failureReasonLines()
                        .get(0)
                        .startsWith("2 x blocksCompressed")
        );

        assertEquals(
                10,
                diagnostics.skippedNotes()
                        .size()
        );

        assertEquals(
                3,
                diagnostics.failureSamples()
                        .size()
        );
    }

    @Test
    void aggregatesLiquidDecodeFailuresSeparatelyFromChunkFailures() {
        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        diagnostics.recordParsed();
        diagnostics.recordParsed();
        diagnostics.recordLiquidDecodeFailure(
                "liquidsCompressed: zstd bit-plane decompression failed"
        );
        diagnostics.recordLiquidDecodeFailure(
                "liquidsCompressed: zstd bit-plane decompression failed"
        );

        assertEquals(
                2,
                diagnostics.parsed()
        );

        assertEquals(
                0,
                diagnostics.failed()
        );

        assertEquals(
                2,
                diagnostics.liquidDecodeFailures()
        );

        assertEquals(
                2,
                diagnostics.liquidFailureReasons()
                        .get("liquidsCompressed: zstd bit-plane decompression failed")
        );

        assertEquals(
                "2 x liquidsCompressed: zstd bit-plane decompression failed",
                diagnostics.liquidFailureReasonLines()
                        .get(0)
        );
    }
}
