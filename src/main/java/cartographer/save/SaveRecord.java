package cartographer.save;

import java.util.Map;

public record SaveRecord(
        Map<String, Object> columns,
        byte[] payload
) {
}