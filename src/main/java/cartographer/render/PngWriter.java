package cartographer.render;

import cartographer.cli.CommandException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PngWriter {
    public void write(BufferedImage image, Path output) {
        try {
            Path parent = output.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!ImageIO.write(image, "png", output.toFile())) {
                throw new CommandException("PNG writer is not available");
            }
        } catch (IOException exception) {
            throw new CommandException("Cannot write PNG: " + output + " (" + exception.getMessage() + ")", exception);
        }
    }
}
