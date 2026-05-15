package com.ihh.wpBot.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MediaSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/api/media/test-media-id", "/api/conversations/messages/1/media"})
    void mediaEndpoints_requireAuthentication(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 401 && status != 403) {
                        throw new AssertionError("Expected 401/403 but was " + status + " for " + endpoint);
                    }
                });
    }

    @Test
    void publicMediaEndpoint_doesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/media/public/non-existing.jpg"))
                .andExpect(status().isNotFound());
    }
}
