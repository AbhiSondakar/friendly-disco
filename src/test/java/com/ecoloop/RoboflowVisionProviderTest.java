package com.ecoloop;

import com.ecoloop.classification.api.ClassificationResult;
import com.ecoloop.classification.internal.RoboflowVisionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RoboflowVisionProviderTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private RoboflowVisionProvider provider;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder().baseUrl("https://serverless.roboflow.com");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        ObjectProvider providerMock = new ObjectProvider() {
            @Override public Object getObject() { return null; }
            @Override public Object getObject(Object... args) { return null; }
            @Override public Object getIfAvailable() { return null; }
            @Override public Object getIfUnique() { return null; }
        };

        provider = new RoboflowVisionProvider(restClient, "test-api-key", "test-workflow", providerMock);
    }

    @Test
    void testSuccessfulClassification() {
        String jsonResponse = """
            {
              "outputs": [
                {
                  "predictions": [
                    { "class": "laptop computer", "confidence": 0.94 }
                  ]
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://serverless.roboflow.com/test-workflow"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        byte[] fakeImage = new byte[] { (byte)0xFF, (byte)0xD8, (byte)0xFF, 0x00 };
        ClassificationResult result = provider.classify(fakeImage, "image/jpeg");

        mockServer.verify();
        assertEquals("laptop", result.category());
        assertEquals(0.94, result.confidence(), 0.001);
        assertEquals("completed", result.status());
        assertTrue(result.isSuccessful());
    }

    @Test
    void testLowConfidenceTriggersManualReview() {
        String jsonResponse = """
            {
              "outputs": [
                {
                  "predictions": [
                    { "class": "phone", "confidence": 0.15 }
                  ]
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://serverless.roboflow.com/test-workflow"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        byte[] fakeImage = new byte[] { (byte)0xFF, (byte)0xD8, (byte)0xFF, 0x00 };
        ClassificationResult result = provider.classify(fakeImage, "image/jpeg");

        mockServer.verify();
        assertEquals("other", result.category());
        assertEquals("manual_review", result.status());
        assertFalse(result.isSuccessful());
    }

    @Test
    void testServerErrorTriggersFailedState() {
        mockServer.expect(requestTo("https://serverless.roboflow.com/test-workflow"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());
        mockServer.expect(requestTo("https://serverless.roboflow.com/test-workflow"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError());

        byte[] fakeImage = new byte[] { (byte)0xFF, (byte)0xD8, (byte)0xFF, 0x00 };
        ClassificationResult result = provider.classify(fakeImage, "image/jpeg");

        mockServer.verify();
        assertEquals("other", result.category());
        assertEquals("failed", result.status());
        assertFalse(result.isSuccessful());
    }
}
