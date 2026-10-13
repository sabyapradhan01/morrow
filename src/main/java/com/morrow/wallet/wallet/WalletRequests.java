package com.morrow.wallet.wallet;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class WalletRequests {
    private WalletRequests() { }

    public record TopUp(
            @NotNull(message = "Enter an amount to add.")
            @DecimalMin(value = "1.00", message = "Top-ups must be at least ₹1.")
            @DecimalMax(value = "1000000.00", message = "Top-ups cannot exceed ₹10,00,000.")
            BigDecimal amount) { }

    public record Transfer(
            @NotBlank(message = "Enter the recipient's registered email.")
            @Email(message = "Enter a valid recipient email.")
            @Size(max = 255, message = "Recipient email is too long.")
            String recipientEmail,
            @NotNull(message = "Enter a transfer amount.")
            @DecimalMin(value = "0.01", message = "Transfers must be greater than ₹0.")
            @DecimalMax(value = "1000000.00", message = "A transfer cannot exceed ₹10,00,000.")
            BigDecimal amount,
            @Size(max = 250, message = "The transfer note must be 250 characters or fewer.")
            String description) { }
}
