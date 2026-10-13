package com.morrow.wallet.admin;

import java.util.List;
import java.util.Map;

import com.morrow.wallet.auth.AuthService;
import com.morrow.wallet.auth.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final AuthService authService;
    private final boolean sqlWorkbenchEnabled;
    public AdminController(AdminService adminService, AuthService authService,
                           @Value("${morrow.admin.sql-workbench-enabled:true}") boolean sqlWorkbenchEnabled) {
        this.adminService = adminService;
        this.authService = authService;
        this.sqlWorkbenchEnabled = sqlWorkbenchEnabled;
    }

    @GetMapping("/features") public Map<String,Object> features(HttpServletRequest r) {
        requireAdmin(r);
        return Map.of("sqlWorkbenchEnabled", sqlWorkbenchEnabled);
    }

    @GetMapping("/overview") public Map<String,Object> overview(HttpServletRequest r) { requireAdmin(r); return adminService.overview(); }
    @GetMapping("/users") public List<Map<String,Object>> users(HttpServletRequest r) { requireAdmin(r); return adminService.users(); }
    @GetMapping("/wallets") public List<Map<String,Object>> wallets(HttpServletRequest r) { requireAdmin(r); return adminService.wallets(); }
    @GetMapping("/transactions") public List<Map<String,Object>> transactions(HttpServletRequest r) { requireAdmin(r); return adminService.transactions(); }
    @GetMapping("/ledger") public List<Map<String,Object>> ledger(HttpServletRequest r) { requireAdmin(r); return adminService.ledger(); }

    @PostMapping("/users") public ResponseEntity<Map<String,Object>> createUser(@Valid @RequestBody AdminRequests.CreateUser body, HttpServletRequest r) {
        requireAdmin(r); return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createUser(body));
    }
    @PutMapping("/users/{id}") public ResponseEntity<Void> updateUser(@PathVariable long id, @Valid @RequestBody AdminRequests.UpdateUser body, HttpServletRequest r) {
        SessionUser admin = requireAdmin(r);
        if (id == admin.userId() && (!"ADMIN".equalsIgnoreCase(body.role()) || !"ACTIVE".equalsIgnoreCase(body.status()))) {
            throw new com.morrow.wallet.api.ApiException(HttpStatus.CONFLICT, "Do not demote or deactivate your own administrator session.");
        }
        adminService.updateUser(id, body); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/users/{id}") public ResponseEntity<Void> closeUser(@PathVariable long id, HttpServletRequest r) {
        SessionUser admin = requireAdmin(r);
        if (id == admin.userId()) throw new com.morrow.wallet.api.ApiException(HttpStatus.CONFLICT, "You cannot close your own administrator account.");
        adminService.closeUser(id); return ResponseEntity.noContent().build();
    }
    @PutMapping("/wallets/{id}") public ResponseEntity<Void> updateWallet(@PathVariable long id, @Valid @RequestBody AdminRequests.WalletBalance body, HttpServletRequest r) {
        requireAdmin(r); adminService.setWalletBalance(id, body.balance()); return ResponseEntity.noContent().build();
    }
    @PutMapping("/transactions/{id}") public ResponseEntity<Void> updateTransaction(@PathVariable long id, @Valid @RequestBody AdminRequests.UpdateTransaction body, HttpServletRequest r) {
        requireAdmin(r); adminService.updateTransactionDescription(id, body); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/transactions/{id}") public ResponseEntity<Void> deleteTransaction(@PathVariable long id, HttpServletRequest r) {
        requireAdmin(r); adminService.deleteTransaction(id); return ResponseEntity.noContent().build();
    }
    @PutMapping("/ledger/{id}") public ResponseEntity<Void> updateLedger(@PathVariable long id, @Valid @RequestBody AdminRequests.UpdateLedger body, HttpServletRequest r) {
        requireAdmin(r); adminService.updateLedger(id, body); return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/ledger/{id}") public ResponseEntity<Void> deleteLedger(@PathVariable long id, HttpServletRequest r) {
        requireAdmin(r); adminService.deleteLedger(id); return ResponseEntity.noContent().build();
    }
    @PostMapping("/sql") public Map<String,Object> executeSql(@Valid @RequestBody AdminRequests.SqlCommand body, HttpServletRequest r) {
        requireAdmin(r);
        if (!sqlWorkbenchEnabled) {
            throw new com.morrow.wallet.api.ApiException(HttpStatus.FORBIDDEN,
                    "The raw SQL workbench is disabled on the hosted demo. Use the local academic environment for DDL/DML demonstrations.");
        }
        return adminService.executeSql(body.sql());
    }

    private SessionUser requireAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        SessionUser sessionUser = SessionUser.require(session);
        SessionUser current = authService.findActiveUser(sessionUser.userId());
        current.store(session);
        current.requireAdmin();
        return current;
    }
}
