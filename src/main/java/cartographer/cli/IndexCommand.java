package cartographer.cli;

import cartographer.save.SaveIndex;
import cartographer.save.SaveIndexReader;
import cartographer.save.TableIndex;

import java.io.PrintStream;
import java.nio.file.Path;

public class IndexCommand implements Command {

    private final PrintStream out;
    private final SaveIndexReader indexReader;

    public IndexCommand(
            PrintStream out,
            SaveIndexReader indexReader
    ) {
        this.out = out;
        this.indexReader = indexReader;
    }

    @Override
    public void run(
            String[] args
    ) {
        if (args.length < 1) {
            throw new CommandException(
                    "Usage: index <save.vcdbs>"
            );
        }

        SaveIndex index =
                indexReader.read(
                        Path.of(
                                args[0]
                        ),
                        new ProgressReporter(
                                out
                        )
                );

        out.println();

        out.println(
                "SAVE INDEX"
        );

        for (TableIndex table :
                index.tables()) {

            out.printf(
                    "%s: %d rows",
                    table.tableName(),
                    table.rows()
            );

            if (table.minPosition() != null
                    && table.maxPosition() != null) {

                out.printf(
                        ", raw position range %d..%d",
                        table.minPosition(),
                        table.maxPosition()
                );
            }

            out.println();
        }
    }
}