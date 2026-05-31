package com.wex.anomaly.service;

import com.wex.anomaly.model.AnalysisResult;
import com.wex.anomaly.model.TransactionAnalysis;
import com.wex.anomaly.model.TransactionRequest;
import com.wex.anomaly.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {

    private final OpenAIService openAIService;
    private final KafkaPublisherService kafkaPublisherService;
    private final TransactionRepository transactionRepository;

    public AnalysisResult analyse(TransactionRequest request) {
        log.info("Analysing transaction: {}", request.getTransactionId());

        var existing = transactionRepository.findByTransactionId(request.getTransactionId());
        if (existing.isPresent()) {
            log.warn("Duplicate: {} returning cached", request.getTransactionId());
            return mapToResult(existing.get());
        }

        OpenAIService.OpenAIResult aiResult = openAIService.analyse(request);

        AnalysisResult result = AnalysisResult.builder()
                .transactionId(request.getTransactionId())
                .status(aiResult.status)
                .reason(aiResult.reason)
                .riskLevel(aiResult.riskLevel)
                .department(aiResult.department)
                .analysedAt(Instant.now())
                .build();

        String topic = kafkaPublisherService.publish(result);
        result.setKafkaTopic(topic);
        saveToDatabase(request, result);

        log.info("Done: {} -> {}", request.getTransactionId(), result.getStatus());
        return result;
    }

    private void saveToDatabase(TransactionRequest request, AnalysisResult result) {
        TransactionAnalysis entity = TransactionAnalysis.builder()
                .transactionId(request.getTransactionId())
                .amount(request.getAmount())
                .merchant(request.getMerchant())
                .cardHolder(request.getCardHolder())
                .location(request.getLocation())
                .usualSpend(request.getUsualSpend())
                .transactionTime(request.getTransactionTime())
                .status(result.getStatus())
                .riskLevel(result.getRiskLevel())
                .aiReason(result.getReason())
                .kafkaTopic(result.getKafkaTopic())
                .analysedAt(result.getAnalysedAt())
                .build();
        transactionRepository.save(entity);
    }

    private AnalysisResult mapToResult(TransactionAnalysis entity) {
        return AnalysisResult.builder()
                .transactionId(entity.getTransactionId())
                .status(entity.getStatus())
                .riskLevel(entity.getRiskLevel())
                .reason(entity.getAiReason() + " [CACHED]")
                .kafkaTopic(entity.getKafkaTopic())
                .analysedAt(entity.getAnalysedAt())
                .build();
    }
}
