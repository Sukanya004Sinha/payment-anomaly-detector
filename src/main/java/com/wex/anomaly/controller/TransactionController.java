package com.wex.anomaly.controller;

import com.wex.anomaly.model.AnalysisResult;
import com.wex.anomaly.model.TransactionAnalysis;
import com.wex.anomaly.model.TransactionRequest;
import com.wex.anomaly.repository.TransactionRepository;
import com.wex.anomaly.service.AnomalyDetectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction Analysis")
public class TransactionController {

    private final AnomalyDetectionService anomalyDetectionService;
    private final TransactionRepository transactionRepository;

    @Operation(
            summary = "Analyse a payment transaction",
            description = "Submits a transaction for AI analysis. Returns NORMAL, SUSPICIOUS, NEEDS_REVIEW, or FALLBACK. Duplicate transactionIds return cached results."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transaction analysed successfully",
                    content = @Content(schema = @Schema(implementation = AnalysisResult.class),
                            examples = @ExampleObject(value = """
                    {
                        "transactionId": "TXN-001",
                        "status": "SUSPICIOUS",
                        "riskLevel": "HIGH",
                        "reason": "Amount is 28x above usual spend, unknown merchant, 3AM",
                        "department": null,
                        "kafkaTopic": "suspicious-transactions",
                        "analysedAt": "2025-11-01T03:14:22Z"
                    }
                """))),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping("/analyse")
    public ResponseEntity<AnalysisResult> analyseTransaction(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Transaction to analyse",
                    required = true,
                    content = @Content(examples = {
                            @ExampleObject(name = "Suspicious", value = """
                        {
                            "transactionId": "TXN-SUSP-001",
                            "amount": 85000,
                            "merchant": "Unknown Vendor XYZ",
                            "merchantCategory": "UNKNOWN",
                            "cardHolder": "John Driver",
                            "location": "Mumbai",
                            "usualSpend": 3000,
                            "transactionTime": "03:00"
                        }
                    """),
                            @ExampleObject(name = "Normal", value = """
                        {
                            "transactionId": "TXN-NORM-001",
                            "amount": 55,
                            "merchant": "Shell Fuel Station",
                            "merchantCategory": "FUEL",
                            "cardHolder": "John Driver",
                            "location": "London",
                            "usualSpend": 60,
                            "transactionTime": "10:30"
                        }
                    """),
                            @ExampleObject(name = "Needs Review", value = """
                        {
                            "transactionId": "TXN-REV-001",
                            "amount": 15000,
                            "merchant": "EV Charging Network",
                            "merchantCategory": "EV_CHARGING",
                            "cardHolder": "Bob Fleet",
                            "location": "Manchester",
                            "usualSpend": 3000,
                            "transactionTime": "14:00"
                        }
                    """)
                    }))
            @RequestBody @Valid TransactionRequest request) {
        log.info("Received transaction: {}", request.getTransactionId());
        return ResponseEntity.ok(anomalyDetectionService.analyse(request));
    }

    @Operation(summary = "Get all transactions", description = "Returns all analysed transactions from the database")
    @GetMapping
    public ResponseEntity<List<TransactionAnalysis>> getAllTransactions() {
        return ResponseEntity.ok(transactionRepository.findAll());
    }

    @Operation(summary = "Filter by status", description = "Filter transactions by NORMAL, SUSPICIOUS, NEEDS_REVIEW, or FALLBACK")
    @GetMapping("/status/{status}")
    public ResponseEntity<List<TransactionAnalysis>> getByStatus(
            @Parameter(description = "Status to filter by", example = "SUSPICIOUS")
            @PathVariable AnalysisResult.TransactionStatus status) {
        return ResponseEntity.ok(transactionRepository.findByStatus(status));
    }

    @Operation(summary = "Health check")
    @Tag(name = "Health")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Payment Anomaly Detector is running");
    }
}