package com.ihh.wpBot.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WhatsAppServiceTest {

    @Test
    void sendTemplateMessage_stillCallsMetaMessagesEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        WhatsAppService service = new WhatsAppService(restTemplate);
        ReflectionTestUtils.setField(service, "phoneId", "1234567890");
        ReflectionTestUtils.setField(service, "accessToken", "TEST_TOKEN");

        server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890/messages"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.test\"}]}", MediaType.APPLICATION_JSON));

        service.sendTemplateMessage("905551234567", "template_name", "tr");

        server.verify();
    }
}

