package com.morrow.wallet.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.morrow.wallet.api.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Pattern FIRST_TOKEN = Pattern.compile("^([A-Za-z]+)\\b");
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

    public AdminService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("users", count("SELECT COUNT(*) FROM USERS"));
        out.put("activeUsers", count("SELECT COUNT(*) FROM USERS WHERE STATUS = 'ACTIVE'"));
        out.put("wallets", count("SELECT COUNT(*) FROM WALLETS"));
        out.put("transactions", count("SELECT COUNT(*) FROM TRANSACTIONS"));
        out.put("ledgerEntries", count("SELECT COUNT(*) FROM LEDGER"));
        out.put("totalBalance", jdbc.queryForObject("SELECT NVL(SUM(BALANCE), 0) FROM WALLETS", BigDecimal.class));
        return out;
    }

    public List<Map<String, Object>> users() {
        return jdbc.queryForList("""
                SELECT u.USER_ID, u.FULL_NAME, u.EMAIL, u.STATUS, u.ROLE, u.CREATED_AT,
                       w.WALLET_ID, w.BALANCE
                FROM USERS u LEFT JOIN WALLETS w ON w.USER_ID = u.USER_ID
                ORDER BY u.USER_ID DESC
                """);
    }
    public List<Map<String, Object>> wallets() {
        return jdbc.queryForList("""
                SELECT w.WALLET_ID, w.USER_ID, u.FULL_NAME, u.EMAIL,
                       w.CURRENCY_CODE, w.BALANCE, w.CREATED_AT
                FROM WALLETS w JOIN USERS u ON u.USER_ID = w.USER_ID ORDER BY w.WALLET_ID DESC
                """);
    }
    public List<Map<String, Object>> transactions() {
        return jdbc.queryForList("""
                SELECT t.TRANSACTION_ID, t.REFERENCE_CODE, t.TRANSACTION_TYPE,
                       t.SENDER_WALLET_ID, t.RECEIVER_WALLET_ID, t.AMOUNT, t.STATUS,
                       t.DESCRIPTION, t.CREATED_AT
                FROM TRANSACTIONS t ORDER BY t.TRANSACTION_ID DESC
                """);
    }
    public List<Map<String, Object>> ledger() {
        return jdbc.queryForList("""
                SELECT l.LEDGER_ID, l.TRANSACTION_ID, l.WALLET_ID,
                       u.EMAIL, l.ENTRY_TYPE, l.AMOUNT, l.BALANCE_AFTER, l.CREATED_AT
                FROM LEDGER l JOIN WALLETS w ON w.WALLET_ID = l.WALLET_ID
                JOIN USERS u ON u.USER_ID = w.USER_ID ORDER BY l.LEDGER_ID DESC
                """);
    }

    @Transactional
    public Map<String, Object> createUser(AdminRequests.CreateUser input) {
        String name = cleanName(input.fullName());
        String email = normalizeEmail(input.email());
        String password = input.password();
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password must be no longer than 72 UTF-8 bytes.");
        String role = normalizeRole(input.role());
        try {
            jdbc.update("INSERT INTO USERS (FULL_NAME, EMAIL, PASSWORD_HASH, STATUS, ROLE) VALUES (?, ?, ?, 'ACTIVE', ?)",
                    name, email, passwordEncoder.encode(password), role);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "That email address is already registered.");
        }
        Long userId = jdbc.queryForObject("SELECT USER_ID FROM USERS WHERE LOWER(EMAIL) = ?", Long.class, email);
        jdbc.update("INSERT INTO WALLETS (USER_ID, CURRENCY_CODE, BALANCE) VALUES (?, 'INR', 0)", userId);
        return jdbc.queryForMap("SELECT USER_ID, FULL_NAME, EMAIL, STATUS, ROLE FROM USERS WHERE USER_ID = ?", userId);
    }

    @Transactional
    public void updateUser(long userId, AdminRequests.UpdateUser input) {
        String name = cleanName(input.fullName());
        String email = normalizeEmail(input.email());
        String status = normalizeStatus(input.status());
        String role = normalizeRole(input.role());
        Map<String, Object> current;
        try { current = jdbc.queryForMap("SELECT ROLE, STATUS FROM USERS WHERE USER_ID = ? FOR UPDATE", userId); }
        catch (EmptyResultDataAccessException ex) { throw new ApiException(HttpStatus.NOT_FOUND, "User not found."); }
        ensureAdminNotLockedOut(userId, String.valueOf(current.get("ROLE")), String.valueOf(current.get("STATUS")), role, status);
        try { jdbc.update("UPDATE USERS SET FULL_NAME=?, EMAIL=?, STATUS=?, ROLE=? WHERE USER_ID=?", name, email, status, role, userId); }
        catch (DuplicateKeyException ex) { throw new ApiException(HttpStatus.CONFLICT, "That email address is already registered."); }
        if (input.newPassword() != null && !input.newPassword().isBlank()) {
            int length = input.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (length < 8 || length > 72) throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be 8–72 UTF-8 bytes, or leave it blank.");
            jdbc.update("UPDATE USERS SET PASSWORD_HASH=? WHERE USER_ID=?", passwordEncoder.encode(input.newPassword()), userId);
        }
    }

    @Transactional
    public void closeUser(long userId) {
        Map<String, Object> current;
        try { current = jdbc.queryForMap("SELECT ROLE, STATUS FROM USERS WHERE USER_ID = ? FOR UPDATE", userId); }
        catch (EmptyResultDataAccessException ex) { throw new ApiException(HttpStatus.NOT_FOUND, "User not found."); }
        ensureAdminNotLockedOut(userId, String.valueOf(current.get("ROLE")), String.valueOf(current.get("STATUS")), "USER", "CLOSED");
        // Soft close: preserve financial history and all foreign-key relationships.
        jdbc.update("UPDATE USERS SET STATUS='CLOSED' WHERE USER_ID=?", userId);
    }

    @Transactional
    public void setWalletBalance(long walletId, BigDecimal requestedBalance) {
        BigDecimal target = normalizeMoney(requestedBalance);
        if (target.signum() < 0 || target.compareTo(new BigDecimal("999999999999.99")) > 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Wallet balance must be non-negative and within Oracle NUMBER range.");
        BigDecimal current;
        try { current = jdbc.queryForObject("SELECT BALANCE FROM WALLETS WHERE WALLET_ID=? FOR UPDATE", BigDecimal.class, walletId); }
        catch (EmptyResultDataAccessException ex) { throw new ApiException(HttpStatus.NOT_FOUND, "Wallet not found."); }
        current = normalizeMoney(current);
        BigDecimal delta = target.subtract(current);
        if (delta.signum() == 0) return;
        boolean credit = delta.signum() > 0;
        BigDecimal amount = delta.abs();
        String reference = "MRW-ADM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
        jdbc.update("UPDATE WALLETS SET BALANCE=? WHERE WALLET_ID=?", target, walletId);
        if (credit) {
            jdbc.update("INSERT INTO TRANSACTIONS (REFERENCE_CODE, TRANSACTION_TYPE, SENDER_WALLET_ID, RECEIVER_WALLET_ID, AMOUNT, STATUS, DESCRIPTION) VALUES (?, 'TOP_UP', NULL, ?, ?, 'COMPLETED', 'Admin balance adjustment (simulated)')", reference, walletId, amount);
        } else {
            jdbc.update("INSERT INTO TRANSACTIONS (REFERENCE_CODE, TRANSACTION_TYPE, SENDER_WALLET_ID, RECEIVER_WALLET_ID, AMOUNT, STATUS, DESCRIPTION) VALUES (?, 'PAYMENT', ?, NULL, ?, 'COMPLETED', 'Admin balance adjustment (simulated)')", reference, walletId, amount);
        }
        Long transactionId = jdbc.queryForObject("SELECT TRANSACTION_ID FROM TRANSACTIONS WHERE REFERENCE_CODE=?", Long.class, reference);
        jdbc.update("INSERT INTO LEDGER (WALLET_ID, TRANSACTION_ID, ENTRY_TYPE, AMOUNT, BALANCE_AFTER) VALUES (?, ?, ?, ?, ?)", walletId, transactionId, credit ? "CREDIT" : "DEBIT", amount, target);
    }

    public void updateTransactionDescription(long transactionId, AdminRequests.UpdateTransaction input) {
        if (jdbc.update("UPDATE TRANSACTIONS SET DESCRIPTION=? WHERE TRANSACTION_ID=?", input.description() == null ? null : input.description().trim(), transactionId) == 0)
            throw new ApiException(HttpStatus.NOT_FOUND, "Transaction not found.");
    }

    @Transactional
    public void deleteTransaction(long transactionId) {
        List<Long> affected = jdbc.queryForList("SELECT DISTINCT WALLET_ID FROM LEDGER WHERE TRANSACTION_ID=?", Long.class, transactionId);
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM TRANSACTIONS WHERE TRANSACTION_ID=?", Integer.class, transactionId);
        if (exists == null || exists == 0) throw new ApiException(HttpStatus.NOT_FOUND, "Transaction not found.");
        jdbc.update("DELETE FROM LEDGER WHERE TRANSACTION_ID=?", transactionId);
        jdbc.update("DELETE FROM TRANSACTIONS WHERE TRANSACTION_ID=?", transactionId);
        for (Long walletId : affected) rebuildWalletFromLedger(walletId);
    }

    @Transactional
    public void updateLedger(long ledgerId, AdminRequests.UpdateLedger input) {
        String type = input.entryType() == null ? "" : input.entryType().trim().toUpperCase(Locale.ROOT);
        if (!List.of("CREDIT", "DEBIT").contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST, "Entry type must be CREDIT or DEBIT.");
        BigDecimal amount = normalizeMoney(input.amount());
        if (amount.signum() <= 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Ledger amount must be greater than zero.");
        Long walletId;
        try { walletId = jdbc.queryForObject("SELECT WALLET_ID FROM LEDGER WHERE LEDGER_ID=? FOR UPDATE", Long.class, ledgerId); }
        catch (EmptyResultDataAccessException ex) { throw new ApiException(HttpStatus.NOT_FOUND, "Ledger entry not found."); }
        jdbc.update("UPDATE LEDGER SET ENTRY_TYPE=?, AMOUNT=? WHERE LEDGER_ID=?", type, amount, ledgerId);
        rebuildWalletFromLedger(walletId);
    }

    @Transactional
    public void deleteLedger(long ledgerId) {
        Long walletId;
        try { walletId = jdbc.queryForObject("SELECT WALLET_ID FROM LEDGER WHERE LEDGER_ID=? FOR UPDATE", Long.class, ledgerId); }
        catch (EmptyResultDataAccessException ex) { throw new ApiException(HttpStatus.NOT_FOUND, "Ledger entry not found."); }
        jdbc.update("DELETE FROM LEDGER WHERE LEDGER_ID=?", ledgerId);
        rebuildWalletFromLedger(walletId);
    }

    private void rebuildWalletFromLedger(long walletId) {
        List<LedgerRow> rows = jdbc.query("SELECT LEDGER_ID, ENTRY_TYPE, AMOUNT FROM LEDGER WHERE WALLET_ID=? ORDER BY CREATED_AT, LEDGER_ID",
                (rs, n) -> new LedgerRow(rs.getLong("LEDGER_ID"), rs.getString("ENTRY_TYPE"), normalizeMoney(rs.getBigDecimal("AMOUNT"))), walletId);
        BigDecimal running = ZERO;
        for (LedgerRow row : rows) {
            running = "CREDIT".equals(row.type()) ? running.add(row.amount()) : running.subtract(row.amount());
            if (running.signum() < 0) throw new ApiException(HttpStatus.CONFLICT, "This edit would create a negative ledger balance. Change rolled back.");
            jdbc.update("UPDATE LEDGER SET BALANCE_AFTER=? WHERE LEDGER_ID=?", running, row.ledgerId());
        }
        jdbc.update("UPDATE WALLETS SET BALANCE=? WHERE WALLET_ID=?", running, walletId);
    }

    private void ensureAdminNotLockedOut(long id, String oldRole, String oldStatus, String newRole, String newStatus) {
        boolean remove = "ADMIN".equalsIgnoreCase(oldRole) && "ACTIVE".equalsIgnoreCase(oldStatus)
                && (!"ADMIN".equals(newRole) || !"ACTIVE".equals(newStatus));
        if (remove) {
            Integer admins = jdbc.queryForObject("SELECT COUNT(*) FROM USERS WHERE ROLE='ADMIN' AND STATUS='ACTIVE'", Integer.class);
            if (admins != null && admins <= 1) throw new ApiException(HttpStatus.CONFLICT, "You cannot deactivate or demote the last active administrator.");
        }
    }

    public Map<String, Object> executeSql(String rawSql) {
        String sql = rawSql == null ? "" : rawSql.trim();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).trim();
        if (sql.isBlank() || sql.contains(";") || sql.contains("--") || sql.contains("/*") || sql.contains("*/"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter one SQL statement only. Multi-statement input and SQL comments are disabled.");
        if (Pattern.compile("\\bPASSWORD_HASH\\b", Pattern.CASE_INSENSITIVE).matcher(sql).find())
            throw new ApiException(HttpStatus.FORBIDDEN, "Password hashes are not available through the SQL workbench.");
        Matcher matcher = FIRST_TOKEN.matcher(sql);
        if (!matcher.find()) throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a supported SQL statement.");
        String command = matcher.group(1).toUpperCase(Locale.ROOT);
        List<String> reads = List.of("SELECT", "WITH");
        List<String> dml = List.of("INSERT", "UPDATE", "DELETE", "MERGE");
        List<String> ddl = List.of("CREATE", "ALTER", "DROP", "TRUNCATE", "RENAME", "COMMENT");
        List<String> dcl = List.of("GRANT", "REVOKE");
        if (!(reads.contains(command) || dml.contains(command) || ddl.contains(command) || dcl.contains(command)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Allowed: SELECT, INSERT, UPDATE, DELETE, MERGE, CREATE, ALTER, DROP, TRUNCATE, RENAME, COMMENT, GRANT, REVOKE. Use SQL*Plus for TCL commands.");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("command", command);
        if (reads.contains(command)) {
            List<Map<String, Object>> rows = jdbc.query(sql, (ResultSetExtractor<List<Map<String, Object>>>) rs -> {
                List<Map<String, Object>> output = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();
                while (rs.next() && output.size() < 200) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columns; i++) {
                        String label = meta.getColumnLabel(i);
                        if (!"PASSWORD_HASH".equalsIgnoreCase(label)) row.put(label, rs.getObject(i));
                    }
                    output.add(row);
                }
                return output;
            });
            result.put("rows", rows); result.put("rowCount", rows.size()); result.put("truncated", rows.size() == 200);
            result.put("message", "Query completed; at most 200 rows are returned.");
        } else if (dml.contains(command)) {
            int affected = jdbc.update(sql);
            result.put("affectedRows", affected); result.put("message", command + " completed; " + affected + " row(s) affected.");
        } else {
            jdbc.execute(sql);
            result.put("message", command + " statement executed. Oracle DDL may commit implicitly.");
        }
        return result;
    }

    private long count(String sql) { Long n = jdbc.queryForObject(sql, Long.class); return n == null ? 0 : n; }
    private static String cleanName(String value) {
        String s = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (s.length() < 2 || s.length() > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "Name must be 2–100 characters.");
        return s;
    }
    private static String normalizeEmail(String value) {
        String s = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (s.length() > 255 || !s.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        return s;
    }
    private static String normalizeRole(String value) {
        String s = value == null ? "USER" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("USER", "ADMIN").contains(s)) throw new ApiException(HttpStatus.BAD_REQUEST, "Role must be USER or ADMIN.");
        return s;
    }
    private static String normalizeStatus(String value) {
        String s = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "SUSPENDED", "CLOSED").contains(s)) throw new ApiException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE, SUSPENDED or CLOSED.");
        return s;
    }
    private static BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a valid monetary amount.");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new ApiException(HttpStatus.BAD_REQUEST, "Amounts may have at most two decimal places."); }
    }
    private record LedgerRow(long ledgerId, String type, BigDecimal amount) { }
}
