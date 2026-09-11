package cartographer.save;

public enum SaveTable {
    PLAYERDATA("playerdata"),
    MAPCHUNK("mapchunk"),
    CHUNK("chunk"),
    GAMEDATA("gamedata");

    private final String tableName;

    SaveTable(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
