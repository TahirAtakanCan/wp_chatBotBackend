package com.ihh.wpBot.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
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

    @Test
    void sendImageMessage_callsMetaMessagesEndpointWithImageType() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        WhatsAppService service = new WhatsAppService(restTemplate);
        ReflectionTestUtils.setField(service, "phoneId", "1234567890");
        ReflectionTestUtils.setField(service, "accessToken", "TEST_TOKEN");

        server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"image\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"caption\":\"Merhaba")))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.image.test\"}]}", MediaType.APPLICATION_JSON));

        service.sendImageMessage("905551234567", "https://example.com/photo.jpg", "Merhaba 😊");

        server.verify();
    }

    @Test
    void sendVideoMessage_callsMetaMessagesEndpointWithVideoType() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        WhatsAppService service = new WhatsAppService(restTemplate);
        ReflectionTestUtils.setField(service, "phoneId", "1234567890");
        ReflectionTestUtils.setField(service, "accessToken", "TEST_TOKEN");

        server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"video\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"caption\":\"video caption\"")))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.video.test\"}]}", MediaType.APPLICATION_JSON));

        service.sendVideoMessage("905551234567", "https://example.com/video.mp4", "video caption");

        server.verify();
    }

    @Test
    void sendDocumentMessage_callsMetaMessagesEndpointWithDocumentTypeAndFilename() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        WhatsAppService service = new WhatsAppService(restTemplate);
        ReflectionTestUtils.setField(service, "phoneId", "1234567890");
        ReflectionTestUtils.setField(service, "accessToken", "TEST_TOKEN");

        server.expect(requestTo("https://graph.facebook.com/v18.0/1234567890/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"type\":\"document\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"filename\":\"guide.pdf\"")))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.document.test\"}]}", MediaType.APPLICATION_JSON));

        service.sendDocumentMessage("905551234567", "https://example.com/guide.pdf", "guide.pdf", "doc caption");

        server.verify();
    }
}

