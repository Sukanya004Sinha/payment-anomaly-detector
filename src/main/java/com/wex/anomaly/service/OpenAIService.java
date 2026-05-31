package com.wex.anomaly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wex.anomaly.model.AnalysisResult;
import com.wex.anomaly.model.TransactionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAIService {

    private final RestClient openAIRestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.model:gpt-3.5-turbo}")
    private String model;

    @Value("${openai.mock-mode:false}")
    private boolean mockMode;

    public OpenAIResult analyse(TransactionRequest request) {
        if (mockMode) {
            log.info("MOCK MODE for: {}", request.getTransactionId());
            return mockAnalyse(request);
        }
        OpenAIResult result = new OpenAIResult();
        result.status = AnalysisResult.TransactionStatus.FALLBACK;
        result.reason = "Fallback";
        try {
            String requestBody = buildOpenAIRequest(request);
            String responseBody = openAIRestClient.post()
                    .uri("/v1/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            parseResponse(responseBody, result);
        } catch (Exception e) {
            log.error("OpenAI call failed: {}", e.getMessage());
            result.reason = "OpenAI call failed: " + e.getMessage();
        }
        return result;
    }

    private OpenAIResult mockAnalyse(TransactionRequest request) {
        OpenAIResult result = new OpenAIResult();
        BigDecimal amount = request.getAmount();
        BigDecimal usualSpend = request.getUsualSpend();
        String category = request.getMerchantCategory() != null ? request.getMerchantCategory() : "UNKNOWN";
        String time = request.getTransactionTime() != null ? request.getTransactionTime() : "12:00";
        boolean isUnknown = category.equals("UNKNOWN");
        boolean isUnusualTime = isUnusualTime(time);
        boolean isVeryHigh = usualSpend != null && amount.compareTo(usualSpend.multiply(BigDecimal.TEN)) > 0;
        boolean isMediumHigh = usualSpend != null && amount.compareTo(usualSpend.multiply(new BigDecimal("3"))) > 0;
        if (isVeryHigh && (isUnknown || isUnusualTime)) {
            result.status = AnalysisResult.TransactionStatus.SUSPICIOUS;
            result.riskLevel = "HIGH";
            result.reason = "[MOCK] Very high amount with unknown merchant or unusual time - HIGH risk";
        } else if (isVeryHigh) {
            result.status = AnalysisResult.TransactionStatus.SUSPICIOUS;
            result.riskLevel = "MEDIUM";
            result.reason = "[MOCK] Amount far exceeds usual spend - flagged suspicious";
        } else if (isMediumHigh || isUnknown) {
            result.status = AnalysisResult.TransactionStatus.NEEDS_REVIEW;
            result.department = isUnknown ? "COMPLIANCE" : "OPS";
            result.reason = "[MOCK] " + (isUnknown ? "Unknown merchant" : "Elevated amount") + " - needs review";
        } else {
            result.status = AnalysisResult.TransactionStatus.NORMAL;
            result.reason = "[MOCK] Transaction within normal parameters - approved";
        }
        log.info("Mock result: {} -> {}", request.getTransactionId(), result.status);
        return result;
    }

    private boolean isUnusualTime(String time) {
        try {
            int hour = Integer.parseInt(time.split(":")[0]);
            return hour >= 1 && hour <= 5;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildOpenAIRequest(TransactionRequest tx) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("max_tokens", 300);
        ArrayNode messages = root.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", "You are a payment anomaly detection assistant. Always call exactly one function.");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", "Transaction: " + tx.getTransactionId() + " Amount: " + tx.getAmount());
        root.set("tools", buildToolDefinitions());
        root.put("tool_choice", "required");
        return objectMapper.writeValueAsString(root);
    }

    private ArrayNode buildToolDefinitions() throws Exception {
        String json = "[{\"type\":\"function\",\"function\":{\"name\":\"mark_normal\",\"description\":\"Normal\",\"parameters\":{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\"},\"confidence\":{\"type\":\"string\",\"enum\":[\"HIGH\",\"MEDIUM\"]}},\"required\":[\"reason\",\"confidence\"]}}},{\"type\":\"function\",\"function\":{\"name\":\"mark_suspicious\",\"description\":\"Suspicious\",\"parameters\":{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\"},\"risk_level\":{\"type\":\"string\",\"enum\":[\"HIGH\",\"MEDIUM\",\"LOW\"]}},\"required\":[\"reason\",\"risk_level\"]}}},{\"type\":\"function\",\"function\":{\"name\":\"mark_needs_review\",\"description\":\"Review\",\"parameters\":{\"type\":\"object\",\"properties\":{\"reason\":{\"type\":\"string\"},\"department\":{\"type\":\"string\",\"enum\":[\"FRAUD\",\"COMPLIANCE\",\"OPS\"]}},\"required\":[\"reason\",\"department\"]}}}]";
        return (ArrayNode) objectMapper.readTree(json);
    }

    private void parseResponse(String responseBody, OpenAIResult result) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (choices.isEmpty()) {
            result.status = AnalysisResult.TransactionStatus.FALLBACK;
            return;
        }
        JsonNode choice = choices.get(0);
        JsonNode toolCalls = choice.path("message").path("tool_calls");
        if (toolCalls.isEmpty()) {
            result.status = AnalysisResult.TransactionStatus.FALLBACK;
            return;
        }
        String functionName = toolCalls.get(0).path("function").path("name").asText();
        JsonNode args = objectMapper.readTree(toolCalls.get(0).path("function").path("arguments").asText());
        switch (functionName) {
            case "mark_normal" -> {
                result.status = AnalysisResult.TransactionStatus.NORMAL;
                result.reason = args.path("reason").asText();
            }
            case "mark_suspicious" -> {
                result.status = AnalysisResult.TransactionStatus.SUSPICIOUS;
                result.reason = args.path("reason").asText();
                result.riskLevel = args.path("risk_level").asText();
            }
            case "mark_needs_review" -> {
                result.status = AnalysisResult.TransactionStatus.NEEDS_REVIEW;
                result.reason = args.path("reason").asText();
                result.department = args.path("department").asText();
            }
            default -> {
                result.status = AnalysisResult.TransactionStatus.FALLBACK;
                result.reason = "Unknown function";
            }
        }
    }

    public static class OpenAIResult {
        public AnalysisResult.TransactionStatus status;
        public String reason;
        public String riskLevel;
        public String department;
    }
}