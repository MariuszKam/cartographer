package cartographer.save;

import java.util.ArrayList;
import java.util.List;

public class ReadDiagnostics {
    private int parsed;
    private int skipped;
    private int failed;
    private int registryBlocks;
    private final List<String> notes = new ArrayList<>();

    public void recordParsed() {
        parsed++;
    }

    public void recordSkipped(String reason) {
        skipped++;
        if (notes.size() < 10) {
            notes.add("skipped: " + reason);
        }
    }

    public void recordFailed(String reason) {
        failed++;
        if (notes.size() < 10) {
            notes.add("failed: " + reason);
        }
    }

    public void missingTable(String tableName) {
        notes.add("missing table: " + tableName);
    }

    public void registryBlocks(int registryBlocks) {
        this.registryBlocks = registryBlocks;
    }

    public int parsed() {
        return parsed;
    }

    public int skipped() {
        return skipped;
    }

    public int failed() {
        return failed;
    }

    public int registryBlocks() {
        return registryBlocks;
    }

    public List<String> notes() {
        return List.copyOf(notes);
    }
}
