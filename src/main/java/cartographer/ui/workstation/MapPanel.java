package cartographer.ui.workstation;

import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;

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

    public ImageView imageView() { return imageView; }
    public ScrollPane preview() { return preview; }
}
