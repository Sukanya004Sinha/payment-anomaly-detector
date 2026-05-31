package com.wex.anomaly.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class AnalysisResult {

    private String transactionId;
    private TransactionStatus status;      // NORMAL, SUSPICIOUS, NEEDS_REVIEW
    private String riskLevel;             // HIGH, MEDIUM, LOW, null
    private String reason;                // AI's explanation
    private String department;            // for NEEDS_REVIEW: FRAUD, COMPLIANCE, OPS
    private String kafkaTopic;            // which topic it was published to
    private Instant analysedAt;

    public enum TransactionStatus {
        NORMAL, SUSPICIOUS, NEEDS_REVIEW, FALLBACK
    }
}
