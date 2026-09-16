package com.secretvault.health;

import com.secretvault.common.config.SecurityConfig;
import com.secretvault.common.logging.CorrelationIdFilter;
import com.secretvault.health.controller.SystemHealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SystemHealthController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class})
@ActiveProfiles("test")
class SystemHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getHealth_shouldReturn200AndUpStatus() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("secretvault-backend"))
                .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }
}
