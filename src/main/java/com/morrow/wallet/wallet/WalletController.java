package com.morrow.wallet.wallet;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import com.morrow.wallet.auth.SessionUser;
import com.morrow.wallet.auth.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    private final WalletService walletService;
    private final AuthService authService;

    public WalletController(WalletService walletService, AuthService authService) {
        this.walletService = walletService;
        this.authService = authService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(HttpServletRequest request) {
        return walletService.summary(currentUser(request).userId());
    }

    @GetMapping("/transactions")
    public List<WalletService.TransactionView> transactions(HttpServletRequest request) {
        return walletService.transactions(currentUser(request).userId());
    }

    @PostMapping("/top-up")
    public Map<String, Object> topUp(@Valid @RequestBody WalletRequests.TopUp body, HttpServletRequest request) {
        return walletService.topUp(currentUser(request).userId(), body.amount());
    }

    @PostMapping("/transfers")
    public Map<String, Object> transfer(@Valid @RequestBody WalletRequests.Transfer body, HttpServletRequest request) {
        return walletService.transfer(currentUser(request).userId(), body);
    }

    private SessionUser currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        SessionUser sessionUser = SessionUser.require(session);
        SessionUser activeUser = authService.findActiveUser(sessionUser.userId());
        activeUser.store(session);
        if (!"USER".equals(activeUser.role())) {
            throw new com.morrow.wallet.api.ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "Administrator accounts must use the admin control room.");
        }
        return activeUser;
    }
}
