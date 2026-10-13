package com.morrow.wallet.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import com.morrow.wallet.wallet.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final WalletService walletService;

    public AuthController(AuthService authService, WalletService walletService) {
        this.authService = authService;
        this.walletService = walletService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequests.Register body, HttpServletRequest request) {
        SessionUser user = authService.register(body);
        establishSession(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(walletService.summary(user.userId()));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody AuthRequests.Login body, HttpServletRequest request) {
        SessionUser user = authService.login(body);
        establishSession(request, user);
        return walletService.summary(user.userId());
    }

    @GetMapping("/me")
    public Map<String, Object> currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        SessionUser sessionUser = SessionUser.require(session);
        SessionUser activeUser = authService.findActiveUser(sessionUser.userId());
        // Refresh the display name/email from Oracle so account changes take effect in an active session.
        activeUser.store(session);
        return walletService.summary(activeUser.userId());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }

    private static void establishSession(HttpServletRequest request, SessionUser user) {
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        user.store(session);
    }
}
