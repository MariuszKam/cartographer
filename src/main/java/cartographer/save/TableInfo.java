package cartographer.save;

import java.util.List;

public record TableInfo(String name, int rowCount, List<ColumnInfo> columns, List<PayloadSample> payloadSamples) {
}
