package com.morrow.wallet.wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.morrow.wallet.api.ApiException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final JdbcTemplate jdbc;

    public WalletService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> summary(long userId) {
        List<Map<String, Object>> rows = jdbc.query("""
                SELECT u.USER_ID, u.FULL_NAME, u.EMAIL, w.WALLET_ID, w.CURRENCY_CODE, w.BALANCE,
                       NVL((SELECT SUM(t.AMOUNT) FROM TRANSACTIONS t WHERE t.RECEIVER_WALLET_ID = w.WALLET_ID AND t.STATUS = 'COMPLETED'), 0) AS TOTAL_RECEIVED,
                       NVL((SELECT SUM(t.AMOUNT) FROM TRANSACTIONS t WHERE t.SENDER_WALLET_ID = w.WALLET_ID AND t.STATUS = 'COMPLETED'), 0) AS TOTAL_SENT
                FROM USERS u JOIN WALLETS w ON w.USER_ID = u.USER_ID
                WHERE u.USER_ID = ? AND u.STATUS = 'ACTIVE' AND w.CURRENCY_CODE = 'INR'
                """, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", rs.getLong("USER_ID"));
            item.put("fullName", rs.getString("FULL_NAME"));
            item.put("email", rs.getString("EMAIL"));
            item.put("walletId", rs.getLong("WALLET_ID"));
            item.put("currencyCode", rs.getString("CURRENCY_CODE").trim());
            item.put("balance", money(rs.getBigDecimal("BALANCE")));
            item.put("totalReceived", money(rs.getBigDecimal("TOTAL_RECEIVED")));
            item.put("totalSent", money(rs.getBigDecimal("TOTAL_SENT")));
            return item;
        }, userId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "Your INR wallet has not been created. Contact the project administrator.");
        }
        return rows.get(0);
    }

    public List<TransactionView> transactions(long userId) {
        long walletId = getWalletId(userId);
        return jdbc.query("""
                SELECT * FROM (
                    SELECT t.TRANSACTION_ID, t.REFERENCE_CODE, t.TRANSACTION_TYPE, t.AMOUNT,
                           t.STATUS, t.DESCRIPTION, t.CREATED_AT,
                           CASE WHEN t.TRANSACTION_TYPE = 'TOP_UP' THEN 'Wallet top-up'
                                WHEN t.SENDER_WALLET_ID = ? THEN NVL(receiver_user.FULL_NAME, 'Morrow user')
                                ELSE NVL(sender_user.FULL_NAME, 'Morrow user') END AS COUNTERPARTY,
                           CASE WHEN t.RECEIVER_WALLET_ID = ? THEN t.AMOUNT ELSE -t.AMOUNT END AS SIGNED_AMOUNT
                    FROM TRANSACTIONS t
                    LEFT JOIN WALLETS sender_wallet ON sender_wallet.WALLET_ID = t.SENDER_WALLET_ID
                    LEFT JOIN USERS sender_user ON sender_user.USER_ID = sender_wallet.USER_ID
                    LEFT JOIN WALLETS receiver_wallet ON receiver_wallet.WALLET_ID = t.RECEIVER_WALLET_ID
                    LEFT JOIN USERS receiver_user ON receiver_user.USER_ID = receiver_wallet.USER_ID
                    WHERE t.SENDER_WALLET_ID = ? OR t.RECEIVER_WALLET_ID = ?
                    ORDER BY t.CREATED_AT DESC, t.TRANSACTION_ID DESC
                ) WHERE ROWNUM <= 200
                """, (rs, rowNum) -> mapTransaction(rs), walletId, walletId, walletId, walletId);
    }

    @Transactional
    public Map<String, Object> topUp(long userId, BigDecimal requestedAmount) {
        BigDecimal amount = normalizedAmount(requestedAmount);
        if (amount.compareTo(BigDecimal.ONE) < 0 || amount.compareTo(new BigDecimal("1000000.00")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a top-up between ₹1 and ₹10,00,000.");
        }
        long walletId = getWalletId(userId);
        WalletRow wallet = lockWallet(walletId);
        BigDecimal after = money(wallet.balance().add(amount));
        jdbc.update("UPDATE WALLETS SET BALANCE = ? WHERE WALLET_ID = ?", after, walletId);

        String reference = referenceCode();
        jdbc.update("""
                INSERT INTO TRANSACTIONS
                    (REFERENCE_CODE, TRANSACTION_TYPE, SENDER_WALLET_ID, RECEIVER_WALLET_ID, AMOUNT, STATUS, DESCRIPTION)
                VALUES (?, 'TOP_UP', NULL, ?, ?, 'COMPLETED', 'Simulated wallet top-up')
                """, reference, walletId, amount);
        long transactionId = transactionId(reference);
        jdbc.update("""
                INSERT INTO LEDGER (WALLET_ID, TRANSACTION_ID, ENTRY_TYPE, AMOUNT, BALANCE_AFTER)
                VALUES (?, ?, 'CREDIT', ?, ?)
                """, walletId, transactionId, amount, after);
        return summary(userId);
    }

    @Transactional
    public Map<String, Object> transfer(long userId, WalletRequests.Transfer request) {
        BigDecimal amount = normalizedAmount(request.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(new BigDecimal("1000000.00")) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a transfer amount greater than ₹0 and no more than ₹10,00,000.");
        }

        String recipientEmail = request.recipientEmail().trim().toLowerCase(Locale.ROOT);
        Recipient recipient;
        try {
            recipient = jdbc.queryForObject("""
                    SELECT u.USER_ID, u.FULL_NAME, u.EMAIL, w.WALLET_ID
                    FROM USERS u JOIN WALLETS w ON w.USER_ID = u.USER_ID
                    WHERE LOWER(u.EMAIL) = ? AND u.STATUS = 'ACTIVE' AND w.CURRENCY_CODE = 'INR'
                    """, (rs, rowNum) -> new Recipient(rs.getLong("USER_ID"), rs.getString("FULL_NAME"), rs.getString("EMAIL"), rs.getLong("WALLET_ID")), recipientEmail);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "No active Morrow account was found for that email.");
        }

        long senderWalletId = getWalletId(userId);
        if (recipient.userId() == userId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot transfer money to your own account.");
        }

        // Always lock both wallet rows in ID order to reduce deadlock risk for simultaneous opposite transfers.
        List<Long> walletIds = new ArrayList<>(List.of(senderWalletId, recipient.walletId()));
        walletIds.sort(Comparator.naturalOrder());
        Map<Long, WalletRow> locked = new HashMap<>();
        for (Long id : walletIds) locked.put(id, lockWallet(id));

        WalletRow sender = locked.get(senderWalletId);
        WalletRow receiver = locked.get(recipient.walletId());
        if (sender.balance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Your available balance is too low for this transfer.");
        }
        BigDecimal senderAfter = money(sender.balance().subtract(amount));
        BigDecimal receiverAfter = money(receiver.balance().add(amount));
        jdbc.update("UPDATE WALLETS SET BALANCE = ? WHERE WALLET_ID = ?", senderAfter, senderWalletId);
        jdbc.update("UPDATE WALLETS SET BALANCE = ? WHERE WALLET_ID = ?", receiverAfter, recipient.walletId());

        String reference = referenceCode();
        String description = request.description() == null || request.description().isBlank()
                ? "Wallet transfer" : request.description().trim();
        jdbc.update("""
                INSERT INTO TRANSACTIONS
                    (REFERENCE_CODE, TRANSACTION_TYPE, SENDER_WALLET_ID, RECEIVER_WALLET_ID, AMOUNT, STATUS, DESCRIPTION)
                VALUES (?, 'TRANSFER', ?, ?, ?, 'COMPLETED', ?)
                """, reference, senderWalletId, recipient.walletId(), amount, description);
        long transactionId = transactionId(reference);
        jdbc.update("""
                INSERT INTO LEDGER (WALLET_ID, TRANSACTION_ID, ENTRY_TYPE, AMOUNT, BALANCE_AFTER)
                VALUES (?, ?, 'DEBIT', ?, ?)
                """, senderWalletId, transactionId, amount, senderAfter);
        jdbc.update("""
                INSERT INTO LEDGER (WALLET_ID, TRANSACTION_ID, ENTRY_TYPE, AMOUNT, BALANCE_AFTER)
                VALUES (?, ?, 'CREDIT', ?, ?)
                """, recipient.walletId(), transactionId, amount, receiverAfter);
        return summary(userId);
    }

    private long getWalletId(long userId) {
        try {
            return jdbc.queryForObject("SELECT WALLET_ID FROM WALLETS WHERE USER_ID = ? AND CURRENCY_CODE = 'INR'", Long.class, userId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "An INR wallet does not exist for this account.");
        }
    }

    private WalletRow lockWallet(long walletId) {
        return jdbc.queryForObject("SELECT WALLET_ID, BALANCE FROM WALLETS WHERE WALLET_ID = ? FOR UPDATE",
                (rs, rowNum) -> new WalletRow(rs.getLong("WALLET_ID"), money(rs.getBigDecimal("BALANCE"))), walletId);
    }

    private long transactionId(String reference) {
        return jdbc.queryForObject("SELECT TRANSACTION_ID FROM TRANSACTIONS WHERE REFERENCE_CODE = ?", Long.class, reference);
    }

    private static TransactionView mapTransaction(ResultSet rs) throws SQLException {
        String type = rs.getString("TRANSACTION_TYPE");
        BigDecimal amount = money(rs.getBigDecimal("SIGNED_AMOUNT"));
        String category = "TOP_UP".equals(type) ? "Add money" : (amount.signum() >= 0 ? "Transfer received" : "Transfer sent");
        String description = rs.getString("DESCRIPTION");
        String detail = description == null || description.isBlank() ? category : description;
        Timestamp timestamp = rs.getTimestamp("CREATED_AT");
        LocalDateTime createdAt = timestamp == null ? LocalDateTime.now() : timestamp.toLocalDateTime();
        return new TransactionView(rs.getString("REFERENCE_CODE"), rs.getString("REFERENCE_CODE"), type,
                rs.getString("COUNTERPARTY"), category, detail, rs.getString("STATUS"), createdAt, amount);
    }

    private static BigDecimal normalizedAmount(BigDecimal value) {
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid amount.");
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Amounts can have at most two decimal places.");
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String referenceCode() {
        return "MRW-" + UUID.randomUUID().toString().replace("-", "").substring(0, 32).toUpperCase(Locale.ROOT);
    }

    private record WalletRow(long walletId, BigDecimal balance) { }
    private record Recipient(long userId, String fullName, String email, long walletId) { }

    public record TransactionView(String id, String referenceCode, String type, String name, String category,
                                  String detail, String status, LocalDateTime createdAt, BigDecimal amount) { }
}
