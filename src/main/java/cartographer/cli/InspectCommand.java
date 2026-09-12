package cartographer.cli;

import cartographer.parser.PlayerPositionCandidate;
import cartographer.save.ColumnInfo;
import cartographer.save.PayloadSample;
import cartographer.save.SaveInspection;
import cartographer.save.SaveInspector;
import cartographer.save.TableInfo;

import java.io.PrintStream;
import java.nio.file.Path;

public class InspectCommand implements Command {

    private final PrintStream out;
    private final SaveInspector inspector;

    public InspectCommand(
            PrintStream out,
            SaveInspector inspector
    ) {
        this.out = out;
        this.inspector = inspector;
    }

    @Override
    public void run(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: inspect <save.vcdbs>"
            );
        }

        SaveInspection inspection =
                inspector.inspect(
                        Path.of(
                                args[0]
                        ),
                        new ProgressReporter(
                                out
                        )
                );

        out.println();

        out.println(
                "SAVE INSPECTION"
        );

        for (TableInfo table :
                inspection.tables()) {

            printTable(
                    table
            );
        }
    }

    private void printTable(
            TableInfo table
    ) {
        out.printf(
                "%nTABLE %s (%d rows)%n",
                table.name(),
                table.rowCount()
        );

        out.println(
                "Columns:"
        );

        for (ColumnInfo column :
                table.columns()) {

            out.printf(
                    "  - %s %s %s%n",
                    column.name(),
                    column.type(),
                    column.nullable()
                            ? "nullable"
                            : "required"
            );
        }

        if (table.payloadSamples()
                .isEmpty()) {

            return;
        }

        out.println(
                "Payload samples:"
        );

        for (PayloadSample sample :
                table.payloadSamples()) {

            out.printf(
                    "  - row %d, column %s, %d bytes%n",
                    sample.rowNumber(),
                    sample.columnName(),
                    sample.byteLength()
            );

            out.println(
                    "    hex:  "
                            + sample.hexPreview()
            );

            out.println(
                    "    text: "
                            + sample.textPreview()
            );

            for (PlayerPositionCandidate candidate :
                    sample.playerPositionCandidates()) {

                out.printf(
                        "    candidate @%d %s score %.1f: X %.3f, Y %.3f, Z %.3f%n",
                        candidate.offset(),
                        candidate.encoding(),
                        candidate.score(),
                        candidate.position().x(),
                        candidate.position().y(),
                        candidate.position().z()
                );
            }
        }
    }
}