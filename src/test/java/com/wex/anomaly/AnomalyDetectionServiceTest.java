package com.wex.anomaly;

import com.wex.anomaly.model.AnalysisResult;
import com.wex.anomaly.model.TransactionRequest;
import com.wex.anomaly.repository.TransactionRepository;
import com.wex.anomaly.service.AnomalyDetectionService;
import com.wex.anomaly.service.KafkaPublisherService;
import com.wex.anomaly.service.OpenAIService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock
    private OpenAIService openAIService;
    @Mock
    private KafkaPublisherService kafkaPublisherService;
    @Mock
    private TransactionRepository transactionRepository;
    @InjectMocks
    private AnomalyDetectionService anomalyDetectionService;

    private TransactionRequest normalTransaction;
    private TransactionRequest suspiciousTransaction;

    @BeforeEach
    void setUp() {
        normalTransaction = new TransactionRequest();
        normalTransaction.setTransactionId("TXN-001");
        normalTransaction.setAmount(new BigDecimal("50.00"));
        normalTransaction.setMerchant("Shell Fuel Station");
        normalTransaction.setCardHolder("John Driver");
        normalTransaction.setLocation("London");
        normalTransaction.setUsualSpend(new BigDecimal("60.00"));
        normalTransaction.setTransactionTime("10:30");
        normalTransaction.setMerchantCategory("FUEL");

        suspiciousTransaction = new TransactionRequest();
        suspiciousTransaction.setTransactionId("TXN-002");
        suspiciousTransaction.setAmount(new BigDecimal("85000.00"));
        suspiciousTransaction.setMerchant("Unknown Vendor XYZ");
        suspiciousTransaction.setCardHolder("Jane Driver");
        suspiciousTransaction.setLocation("Mumbai");
        suspiciousTransaction.setUsualSpend(new BigDecimal("3000.00"));
        suspiciousTransaction.setTransactionTime("03:00");
        suspiciousTransaction.setMerchantCategory("UNKNOWN");
    }

    @Test
    void shouldMarkTransactionAsNormal() {
        OpenAIService.OpenAIResult aiResult = new OpenAIService.OpenAIResult();
        aiResult.status = AnalysisResult.TransactionStatus.NORMAL;
        aiResult.reason = "Amount within expected range";

        when(transactionRepository.findByTransactionId("TXN-001")).thenReturn(Optional.empty());
        when(openAIService.analyse(any())).thenReturn(aiResult);
        when(kafkaPublisherService.publish(any())).thenReturn("normal-transactions");

        AnalysisResult result = anomalyDetectionService.analyse(normalTransaction);

        assertThat(result.getStatus()).isEqualTo(AnalysisResult.TransactionStatus.NORMAL);
        assertThat(result.getKafkaTopic()).isEqualTo("normal-transactions");
        verify(transactionRepository).save(any());
    }

    @Test
    void shouldMarkTransactionAsSuspicious() {
        OpenAIService.OpenAIResult aiResult = new OpenAIService.OpenAIResult();
        aiResult.status = AnalysisResult.TransactionStatus.SUSPICIOUS;
        aiResult.reason = "Amount 28x above usual spend";
        aiResult.riskLevel = "HIGH";

        when(transactionRepository.findByTransactionId("TXN-002")).thenReturn(Optional.empty());
        when(openAIService.analyse(any())).thenReturn(aiResult);
        when(kafkaPublisherService.publish(any())).thenReturn("suspicious-transactions");

        AnalysisResult result = anomalyDetectionService.analyse(suspiciousTransaction);

        assertThat(result.getStatus()).isEqualTo(AnalysisResult.TransactionStatus.SUSPICIOUS);
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
        assertThat(result.getKafkaTopic()).isEqualTo("suspicious-transactions");
    }

    @Test
    void shouldReturnCachedResultForDuplicateTransaction() {
        var cachedEntity = com.wex.anomaly.model.TransactionAnalysis.builder()
                .transactionId("TXN-001")
                .status(AnalysisResult.TransactionStatus.NORMAL)
                .aiReason("Already processed")
                .kafkaTopic("normal-transactions")
                .analysedAt(java.time.Instant.now())
                .build();

        when(transactionRepository.findByTransactionId("TXN-001"))
                .thenReturn(Optional.of(cachedEntity));

        AnalysisResult result = anomalyDetectionService.analyse(normalTransaction);

        verify(openAIService, never()).analyse(any());
        assertThat(result.getReason()).contains("[CACHED]");
    }
}