package com.wex.anomaly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wex.anomaly.config.KafkaConfig;
import com.wex.anomaly.model.AnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaPublisherService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Returns the topic name the message was published to
    public String publish(AnalysisResult result) {
        String topic = resolveTopic(result.getStatus());

        try {
            String message = objectMapper.writeValueAsString(result);
            kafkaTemplate.send(topic, result.getTransactionId(), message)
                    .whenComplete((sendResult, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish to topic {}: {}", topic, ex.getMessage());
                        } else {
                            log.info("Published transaction {} to topic {} (partition: {}, offset: {})",
                                    result.getTransactionId(),
                                    topic,
                                    sendResult.getRecordMetadata().partition(),
                                    sendResult.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Error serialising result for Kafka: {}", e.getMessage());
        }

        return topic;
    }

    private String resolveTopic(AnalysisResult.TransactionStatus status) {
        return switch (status) {
            case NORMAL       -> KafkaConfig.TOPIC_NORMAL;
            case SUSPICIOUS   -> KafkaConfig.TOPIC_SUSPICIOUS;
            case NEEDS_REVIEW -> KafkaConfig.TOPIC_REVIEW;
            case FALLBACK     -> KafkaConfig.TOPIC_REVIEW; // Fallbacks go to review queue
        };
    }
}
