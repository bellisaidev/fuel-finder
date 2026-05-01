package uk.co.fuelfinder.ingestion.raw.orchestrator;

public class ReconciliationException extends RuntimeException {

    private final ReconciliationDecision decision;

    public ReconciliationException(ReconciliationDecision decision) {
        super(decision.message());
        this.decision = decision;
    }

    public ReconciliationDecision getDecision() {
        return decision;
    }
}
