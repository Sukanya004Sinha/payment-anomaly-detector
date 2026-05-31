package com.wex.anomaly.repository;

import com.wex.anomaly.model.AnalysisResult;
import com.wex.anomaly.model.TransactionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionAnalysis, Long> {

    Optional<TransactionAnalysis> findByTransactionId(String transactionId);

    List<TransactionAnalysis> findByStatus(AnalysisResult.TransactionStatus status);

    List<TransactionAnalysis> findByRiskLevel(String riskLevel);
}
