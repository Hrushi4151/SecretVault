package com.secretvault.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secretvault.auth.dto.SendRegistrationOtpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRegistrationOtpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Consecutive send-otp calls for the same email must succeed without 409 DataIntegrityViolationException")
    void testConsecutiveSendOtpForSameEmail() throws Exception {
        String testEmail = "test_user_" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        SendRegistrationOtpRequest req1 = new SendRegistrationOtpRequest(
                testEmail,
                "Password123!Secure",
                "Dev User",
                "DevSecOps Corp"
        );

        // 1. First send-otp call
        mockMvc.perform(post("/api/v1/auth/register/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 2. Second send-otp call for the SAME email (user requested new code or refreshed)
        SendRegistrationOtpRequest req2 = new SendRegistrationOtpRequest(
                testEmail,
                "Password123!SecureNew",
                "Dev User Updated",
                "DevSecOps Corp"
        );

        mockMvc.perform(post("/api/v1/auth/register/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
