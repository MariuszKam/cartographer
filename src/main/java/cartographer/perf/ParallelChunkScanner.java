package cartographer.perf;

import cartographer.model.ParsedChunk;

import java.util.List;
import java.util.function.Consumer;

public class ParallelChunkScanner {
    public void scan(List<ParsedChunk> chunks, Consumer<ParsedChunk> chunkConsumer) {
        chunks.parallelStream().forEach(chunkConsumer);
    }
}
