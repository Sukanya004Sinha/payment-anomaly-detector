package com.wex.anomaly.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransactionRequest {

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Merchant is required")
    private String merchant;

    @NotBlank(message = "Card holder is required")
    private String cardHolder;

    @NotBlank(message = "Location is required")
    private String location;

    // Optional — used by AI to detect anomalies
    private BigDecimal usualSpend;

    private String transactionTime; // e.g. "03:14"

    private String merchantCategory; // e.g. "FUEL", "EV_CHARGING", "UNKNOWN"
}
