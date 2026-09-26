package cartographer.ui.workstation;

/** Minimal UI boundary used by background-operation progress coordination. */
public interface WorkstationProgressView {
    void setStatus(String status);

    void setIndeterminateProgress();

    void setProgress(double current, double total);
}
