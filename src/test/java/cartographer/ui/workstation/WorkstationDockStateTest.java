package cartographer.ui.workstation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationDockStateTest {

    @Test
    void togglesDocksIndependently() {
        WorkstationDockState state = WorkstationDockState.expanded();

        assertTrue(state.contextVisible());
        assertTrue(state.inspectorVisible());

        state = state.toggleContext();
        assertFalse(state.contextVisible());
        assertTrue(state.inspectorVisible());

        state = state.toggleInspector();
        assertFalse(state.contextVisible());
        assertFalse(state.inspectorVisible());

        state = state.toggleContext();
        assertTrue(state.contextVisible());
        assertFalse(state.inspectorVisible());
    }
}
