package cartographer.ui;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CartographerDesktopLauncher {

    private CartographerDesktopLauncher() {
    }

    static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(CartographerDesktopLauncher.class);
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) ->
                logger.error(
                        "Uncaught exception on thread {}",
                        thread.getName(),
                        failure
                )
        );
        logger.info("Starting VS Cartographer");
        try {
            Application.launch(CartographerDesktopApp.class, args);
        } catch (RuntimeException | Error failure) {
            logger.error("VS Cartographer terminated during launch", failure);
            throw failure;
        }
    }
}
