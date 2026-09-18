package cartographer.ui.workstation;

/**
 * Pure visibility state for the two collapsible Workstation side docks.
 */
public record WorkstationDockState(
        boolean contextVisible,
        boolean inspectorVisible
) {
    public static WorkstationDockState expanded() {
        return new WorkstationDockState(true, true);
    }

    public WorkstationDockState toggleContext() {
        return new WorkstationDockState(!contextVisible, inspectorVisible);
    }

    public WorkstationDockState toggleInspector() {
        return new WorkstationDockState(contextVisible, !inspectorVisible);
    }
}
