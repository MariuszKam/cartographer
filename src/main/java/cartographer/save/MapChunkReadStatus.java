package cartographer.save;

/** Terminal source-read state for one requested authoritative mapchunk. */
public enum MapChunkReadStatus {
    PRESENT_DECODED,
    ABSENT,
    PRESENT_UNREADABLE,
    NOT_COMPLETED
}
