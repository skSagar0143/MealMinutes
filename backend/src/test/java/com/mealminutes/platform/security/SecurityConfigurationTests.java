package com.mealminutes.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationTests {
    @Autowired private MockMvc mockMvc;

    @Test
    void healthProbeIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void unknownApiRequestsAreDeniedUntilAuthenticationIsImplemented() throws Exception {
        mockMvc.perform(get("/api/v1/private-resource")).andExpect(status().isForbidden());
    }

    @Test
    void actuatorMetricsAreNotPublic() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
    }
}