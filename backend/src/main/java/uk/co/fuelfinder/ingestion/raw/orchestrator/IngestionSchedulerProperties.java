package uk.co.fuelfinder.ingestion.raw.orchestrator;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuelfinder.ingestion.scheduler")
public class IngestionSchedulerProperties {

    /**
     * Enables or disables scheduled ingestion.
     */
    private boolean enabled = false;

    /**
     * Cron expression for scheduled ingestion.
     */
    private String cron = "0 */30 * * * *";

    /**
     * Retailer name to ingest.
     */
    private String retailerName = "FUEL_FINDER_API";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getRetailerName() {
        return retailerName;
    }

    public void setRetailerName(String retailerName) {
        this.retailerName = retailerName;
    }
}