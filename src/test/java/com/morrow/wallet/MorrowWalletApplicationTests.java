package com.morrow.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MorrowWalletApplicationTests {
    @Test
    void applicationContextLoads() {
        // Confirms Spring can wire the cloud application. PostgreSQL connectivity is tested separately at /health/db.
    }
}
