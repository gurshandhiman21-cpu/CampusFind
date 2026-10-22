package com.campusfind;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Live integration test verifying that the complete application context loads
 * against the target MySQL database.
 *
 * This test is conditionally executed when the DB_PASSWORD environment variable
 * is supplied, distinguishing compilation and unit verification from live database connectivity.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
    named = "DB_PASSWORD",
    matches = ".+",
    disabledReason = "Requires DB_PASSWORD environment variable to connect to MySQL"
)
class CampusFindApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the full Spring Boot context and DataSource initialize when credentials are provided.
    }
}
