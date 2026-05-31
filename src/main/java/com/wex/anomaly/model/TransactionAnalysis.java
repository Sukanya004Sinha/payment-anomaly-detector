package com.wex.anomaly.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transaction_analysis")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String transactionId;

    private BigDecimal amount;
    private String merchant;
    private String cardHolder;
    private String location;
    private BigDecimal usualSpend;
    private String transactionTime;

    @Enumerated(EnumType.STRING)
    private AnalysisResult.TransactionStatus status;

    private String riskLevel;

    @Column(columnDefinition = "TEXT")
    private String aiReason;

    private String kafkaTopic;

    @Column(nullable = false)
    private Instant analysedAt;
}
