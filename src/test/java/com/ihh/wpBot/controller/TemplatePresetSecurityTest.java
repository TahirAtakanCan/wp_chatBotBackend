package com.ihh.wpBot.controller;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class TemplatePresetSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/api/templates/meta", "/api/templates/presets"})
    void templateReadEndpoints_requireAuthentication(String endpoint) throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 401 && status != 403) {
                        throw new AssertionError("Expected 401/403 but was " + status + " for " + endpoint);
                    }
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/templates/presets", "/api/templates/meta/refresh"})
    void templateWriteEndpoints_requireAuthentication(String endpoint) throws Exception {
        mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"x\",\"metaTemplateName\":\"y\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 401 && status != 403) {
                        throw new AssertionError("Expected 401/403 but was " + status + " for " + endpoint);
                    }
                });
    }
}
