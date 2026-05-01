package uk.co.fuelfinder.ingestion.raw.orchestrator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuelfinder.ingestion.reconciliation")
public class IngestionReconciliationProperties {

    /**
     * Controls whether unexplained reconciliation mismatches abort ingestion.
     */
    private ReconciliationAction unexplainedMismatchAction = ReconciliationAction.FAIL;

    public ReconciliationAction getUnexplainedMismatchAction() {
        return unexplainedMismatchAction;
    }

    public void setUnexplainedMismatchAction(ReconciliationAction unexplainedMismatchAction) {
        this.unexplainedMismatchAction = unexplainedMismatchAction;
    }
}
