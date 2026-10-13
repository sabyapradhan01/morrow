package com.morrow.wallet.admin;

import java.math.BigDecimal;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AdminRequests {
    private AdminRequests() { }
    public record CreateUser(@NotBlank @Size(min=2,max=100) String fullName,
                             @NotBlank @Email @Size(max=255) String email,
                             @NotBlank @Size(min=8,max=72) String password,
                             String role) { }
    public record UpdateUser(@NotBlank @Size(min=2,max=100) String fullName,
                             @NotBlank @Email @Size(max=255) String email,
                             @NotBlank String status, @NotBlank String role,
                             String newPassword) { }
    public record WalletBalance(@NotNull BigDecimal balance) { }
    public record UpdateTransaction(@Size(max=250) String description) { }
    public record UpdateLedger(@NotBlank String entryType, @NotNull BigDecimal amount) { }
    public record SqlCommand(@NotBlank @Size(max=12000) String sql) { }
}
