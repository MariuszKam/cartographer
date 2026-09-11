package cartographer.save;

import java.util.Map;

public record SaveRecord(Map<String, Object> columns, byte[] payload) {
    public Object column(String name) {
        return columns.get(name);
    }
}
