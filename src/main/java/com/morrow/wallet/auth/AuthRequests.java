package com.morrow.wallet.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() { }

    public record Register(
            @NotBlank(message = "Enter your full name.")
            @Size(min = 2, max = 100, message = "Your name must be between 2 and 100 characters.")
            String fullName,
            @NotBlank(message = "Enter your email address.")
            @Email(message = "Enter a valid email address.")
            @Size(max = 255, message = "Email address is too long.")
            String email,
            @NotBlank(message = "Create a password.")
            @Size(min = 8, max = 72, message = "Use a password between 8 and 72 characters.")
            String password) { }

    public record Login(
            @NotBlank(message = "Enter your email address.")
            @Email(message = "Enter a valid email address.")
            String email,
            @NotBlank(message = "Enter your password.")
            @Size(max = 72, message = "The password is too long.")
            String password) { }
}
