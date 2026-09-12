package cartographer;

import cartographer.cli.CommandRouter;

public final class Main {

    private Main() {
    }

    static void main(
            String[] args
    ) {
        int exitCode =
                new CommandRouter()
                        .run(
                                args
                        );

        if (exitCode != 0) {
            System.exit(
                    exitCode
            );
        }
    }
}