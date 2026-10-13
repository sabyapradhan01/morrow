package com.morrow.wallet.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.morrow.wallet.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public AuthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public SessionUser register(AuthRequests.Register request) {
        String name = request.fullName().trim().replaceAll("\\s+", " ");
        String email = normalizeEmail(request.email());
        if (name.length() < 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter your full name.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use a password that is no more than 72 UTF-8 bytes long.");
        }
        String passwordHash = passwordEncoder.encode(request.password());
        try {
            jdbc.update("INSERT INTO USERS (FULL_NAME, EMAIL, PASSWORD_HASH, STATUS, ROLE) VALUES (?, ?, ?, 'ACTIVE', 'USER')",
                    name, email, passwordHash);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with that email already exists. Sign in instead.");
        }

        long userId = jdbc.queryForObject("SELECT USER_ID FROM USERS WHERE EMAIL = ?", Long.class, email);
        jdbc.update("INSERT INTO WALLETS (USER_ID, CURRENCY_CODE, BALANCE) VALUES (?, 'INR', 0.00)", userId);
        return new SessionUser(userId, name, email, "USER");
    }

    public SessionUser login(AuthRequests.Login request) {
        return login(request, "USER");
    }

    public SessionUser adminLogin(AuthRequests.Login request) {
        return login(request, "ADMIN");
    }

    private SessionUser login(AuthRequests.Login request, String requiredRole) {
        String email = normalizeEmail(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        var matches = jdbc.query("SELECT USER_ID, FULL_NAME, EMAIL, PASSWORD_HASH, STATUS, ROLE FROM USERS WHERE LOWER(EMAIL) = ?",
                (rs, rowNum) -> new AccountRow(
                        rs.getLong("USER_ID"), rs.getString("FULL_NAME"), rs.getString("EMAIL"),
                        rs.getString("PASSWORD_HASH"), rs.getString("STATUS"), rs.getString("ROLE")), email);

        if (matches.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        AccountRow account = matches.get(0);
        if (!"ACTIVE".equals(account.status())
                || !requiredRole.equals(account.role())
                || !passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect for this login type.");
        }
        return new SessionUser(account.userId(), account.fullName(), account.email(), account.role());
    }

    public SessionUser findActiveUser(long userId) {
        var matches = jdbc.query("SELECT USER_ID, FULL_NAME, EMAIL, ROLE FROM USERS WHERE USER_ID = ? AND STATUS = 'ACTIVE'",
                (rs, rowNum) -> new SessionUser(rs.getLong("USER_ID"), rs.getString("FULL_NAME"), rs.getString("EMAIL"), rs.getString("ROLE")), userId);
        if (matches.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Your account is unavailable. Please sign in again.");
        }
        return matches.get(0);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record AccountRow(long userId, String fullName, String email, String passwordHash, String status, String role) { }
}
