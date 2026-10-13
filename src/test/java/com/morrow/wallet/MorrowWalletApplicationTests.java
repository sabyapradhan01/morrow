package com.morrow.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MorrowWalletApplicationTests {
    @Test
    void applicationContextLoads() {
        // Confirms Spring can wire the Phase 1 application. The Oracle connection is tested separately at /health/db.
    }
}
