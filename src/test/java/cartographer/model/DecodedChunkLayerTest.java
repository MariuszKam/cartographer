package cartographer.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecodedChunkLayerTest {

    @Test
    void builderPopulatesLayerAndCannotBeReusedAfterBuild() {
        DecodedChunkLayer.Builder builder =
                DecodedChunkLayer.builder(2);
        builder.set(0, 7).set(1, 3);

        DecodedChunkLayer layer = builder.build();

        assertEquals(2, layer.length());
        assertEquals(7, layer.valueAt(0));
        assertEquals(3, layer.valueAt(1));
        assertThrows(IllegalStateException.class, () -> builder.set(0, 9));
        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    void copiedArrayCannotMutateLayer() {
        DecodedChunkLayer layer =
                DecodedChunkLayer.builder(2)
                        .set(0, 7)
                        .set(1, 3)
                        .build();

        int[] values = layer.toArray();
        values[0] = 9;

        assertArrayEquals(new int[]{7, 3}, layer.toArray());
    }

    @Test
    void copyOfDefensivelyCopiesSourceArray() {
        int[] source = {7, 3};
        DecodedChunkLayer layer = DecodedChunkLayer.copyOf(source);

        source[0] = 9;

        assertArrayEquals(new int[]{7, 3}, layer.toArray());
    }
}
