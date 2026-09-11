package cartographer.render;

import java.awt.Color;
import java.awt.Graphics2D;

public class MarkerRenderer {
    public void drawCross(Graphics2D graphics, int x, int y, Color color) {
        graphics.setColor(color);
        graphics.drawLine(x - 5, y, x + 5, y);
        graphics.drawLine(x, y - 5, x, y + 5);
        graphics.drawOval(x - 4, y - 4, 8, 8);
    }
}
