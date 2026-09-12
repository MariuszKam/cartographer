package cartographer.save;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadDiagnosticsTest {

    @Test
    void aggregatesRepeatedSkippedReasons() {
        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        diagnostics.recordSkipped(
                "mapchunk outside requested radius"
        );

        diagnostics.recordSkipped(
                "mapchunk outside requested radius"
        );

        diagnostics.recordSkipped(
                "mapchunk outside requested radius"
        );

        diagnostics.recordSkipped(
                "chunk outside requested radius"
        );

        assertEquals(
                4,
                diagnostics.skipped()
        );

        assertEquals(
                List.of(
                        "skipped: 3 x mapchunk outside requested radius",
                        "skipped: 1 x chunk outside requested radius"
                ),
                diagnostics.skippedNotes()
        );
    }

    @Test
    void notesDoNotRepeatEverySkippedRecord() {
        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        for (int index = 0;
             index < 1000;
             index++) {

            diagnostics.recordSkipped(
                    "outside requested radius"
            );
        }

        assertEquals(
                List.of(
                        "skipped: 1000 x outside requested radius"
                ),
                diagnostics.notes()
        );
    }

    @Test
    void missingTableNotesAreDeduplicated() {
        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        diagnostics.missingTable(
                "mapregion"
        );

        diagnostics.missingTable(
                "mapregion"
        );

        assertEquals(
                List.of(
                        "missing table: mapregion"
                ),
                diagnostics.notes()
        );
    }

    @Test
    void failureReasonsRemainAggregatedSeparately() {
        ReadDiagnostics diagnostics =
                new ReadDiagnostics();

        diagnostics.recordFailed(
                "invalid payload"
        );

        diagnostics.recordFailed(
                "invalid payload"
        );

        diagnostics.recordFailed(
                "unexpected field"
        );

        assertEquals(
                List.of(
                        "2 x invalid payload",
                        "1 x unexpected field"
                ),
                diagnostics.failureReasonLines()
        );
    }
}