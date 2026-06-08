package org.springframework.samples.petclinic.api.tracing;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for distributed tracing.
 *
 * This test demonstrates how to verify trace ID propagation between services.
 * It makes a request to the API Gateway, which then calls the visits-service.
 *
 * To fully verify trace ID propagation:
 * 1. Run this test
 * 2. Check the logs of the API Gateway and visits-service
 * 3. Verify that the same trace ID appears in both services' logs
 * 4. Check the Zipkin UI at http://localhost:9411 to see the distributed trace
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TracingIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(TracingIntegrationTest.class);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Tracer tracer;

    /**
     * Test that verifies trace ID propagation.
     *
     * This test makes a request to the API Gateway's owners endpoint,
     * which should create a trace. The trace ID and span ID are logged
     * for manual verification.
     *
     * To fully verify trace ID propagation, check the logs of the API Gateway
     * and the downstream services to ensure the same trace ID appears in all logs.
     */
    @Test
    public void testTraceIdPropagation() {
        // Make a request to the API Gateway
        String url = "http://localhost:" + port + "/api/customer/owners";

        // Log the current trace context before making the request
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            String traceId = currentSpan.context().traceId();
            String spanId = currentSpan.context().spanId();
            log.info("Before request - TraceId: {}, SpanId: {}", traceId, spanId);
        } else {
            log.info("No current span before request");
        }

        // Make the request
        ResponseEntity<String> response = restTemplate.exchange(
            url,
            HttpMethod.GET,
            new HttpEntity<>(new HttpHeaders()),
            String.class
        );

        // Log the current trace context after making the request
        currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            String traceId = currentSpan.context().traceId();
            String spanId = currentSpan.context().spanId();
            log.info("After request - TraceId: {}, SpanId: {}", traceId, spanId);
        } else {
            log.info("No current span after request");
        }

        // Verify the response
        assertNotNull(response.getBody());
        log.info("Response received: {}", response.getStatusCode());

        // Note: To fully verify trace ID propagation, check the logs of all services
        // and verify that the same trace ID appears in all logs.
        // You can also check the Zipkin UI at http://localhost:9411 to see the distributed trace.
    }
}
