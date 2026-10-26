package com.campusfind.config;

import com.campusfind.controller.HealthController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, WebCorsConfig.class})
class CorsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CORS preflight from allowed origin http://localhost:5500 returns 200 with credentials enabled")
    void testCorsAllowedOriginLocalhost5500() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://localhost:5500")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5500"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("CORS preflight from allowed origin http://127.0.0.1:5500 returns 200 with credentials enabled")
    void testCorsAllowedOrigin1275500() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://127.0.0.1:5500")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5500"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("CORS preflight with literal 'null' origin does not receive credentialed CORS headers")
    void testCorsNullOriginRejected() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "null")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("CORS preflight from disallowed origin evil-attacker.com does not return Access-Control-Allow-Origin")
    void testCorsDisallowedOriginRejected() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://evil-attacker.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("CORS preflight from arbitrary unapproved domain does not return Access-Control-Allow-Origin")
    void testCorsArbitraryOriginRejected() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://attacker-controlled.org:5500")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
