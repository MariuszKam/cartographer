package cartographer.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateDownloadProgressTest {

    @Test
    void reportsBoundedPercentage() {
        assertEquals(0, new UpdateDownloadProgress(0, 200).percent());
        assertEquals(50, new UpdateDownloadProgress(100, 200).percent());
        assertEquals(99, new UpdateDownloadProgress(199, 200).percent());
        assertEquals(100, new UpdateDownloadProgress(200, 200).percent());
    }

    @Test
    void rejectsInvalidProgress() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UpdateDownloadProgress(-1, 100)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new UpdateDownloadProgress(101, 100)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new UpdateDownloadProgress(0, 0)
        );
    }
}
