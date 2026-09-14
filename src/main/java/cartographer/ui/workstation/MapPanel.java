package cartographer.ui.workstation;

import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.embed.swing.SwingFXUtils;
import java.awt.image.BufferedImage;

public final class MapPanel extends BorderPane {
    private final ImageView imageView = new ImageView();
    private final ScrollPane preview = new ScrollPane(imageView);

    public MapPanel() {
        preview.setPannable(true);
        preview.setFitToWidth(false);
        preview.setFitToHeight(false);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        setCenter(preview);
    }

    public void show(Image image, int width, int height) {
        imageView.setImage(image);
        imageView.setFitWidth(Math.max(720, width));
        imageView.setFitHeight(Math.max(620, height));
    }

    public void show(BufferedImage image) { show(SwingFXUtils.toFXImage(image, null), image.getWidth(), image.getHeight()); }

}
