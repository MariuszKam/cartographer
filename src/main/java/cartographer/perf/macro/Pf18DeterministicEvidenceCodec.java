package cartographer.perf.macro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Tiny deterministic key/value codec for child evidence; it has no timestamp header. */
final class Pf18DeterministicEvidenceCodec {
    private Pf18DeterministicEvidenceCodec() {
    }

    static void write(Path path, Map<String, String> values) throws IOException {
        Objects.requireNonNull(path);
        String content = new TreeMap<>(values).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n", "", "\n"));
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    static Map<String, String> read(Path path) throws IOException {
        Map<String, String> values = new TreeMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            int separator = line.indexOf('=');
            if (separator <= 0) throw new IOException("malformed PF-1.8 child evidence line");
            values.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return Map.copyOf(values);
    }
}
