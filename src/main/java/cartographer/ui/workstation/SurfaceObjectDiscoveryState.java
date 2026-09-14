package cartographer.ui.workstation;

public enum SurfaceObjectDiscoveryState {
    NOT_SCANNED,
    SCANNING,
    READY,
    EMPTY,
    FAILED;

    public boolean allowsRender(boolean globallyBusy, boolean hasSelection) {
        return !globallyBusy && this == READY && hasSelection;
    }

    public boolean isCurrentFor(boolean keyMatches) {
        return keyMatches && this != NOT_SCANNED && this != SCANNING;
    }
}
