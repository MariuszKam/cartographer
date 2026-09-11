package cartographer.save;

public record TableIndex(String tableName, int rows, Long minPosition, Long maxPosition) {
}
