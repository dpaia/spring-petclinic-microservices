package org.springframework.samples.petclinic.customers.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests to verify trace context logging.
 *
 * These tests verify that:
 * 1. traceId=<value> and spanId=<value> appear in logs for all requests
 * 2. Format is correct (hex values)
 * 3. Trace context is propagated from incoming HTTP headers
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class TracingIntegrationTest {

    private static final Pattern TRACE_ID = Pattern.compile("traceId=[0-9a-fA-F]{16,32}");
    private static final Pattern SPAN_ID  = Pattern.compile("spanId=[0-9a-fA-F]{16}");

    @Autowired
    TestRestTemplate restTemplate;

    /**
     * Test 1: Logs contain traceId=<hex> and spanId=<hex> for GET requests
     */
    @Test
    void shouldLogTraceIdAndSpanIdWithCorrectFormat(CapturedOutput output) {
        String logsBefore = output.getOut();

        restTemplate.getForEntity("/owners", String.class);

        String logs = getLogsDelta(output, logsBefore);

        assertThat(logs)
            .as("Logs should contain traceId=<hex>")
            .containsPattern(TRACE_ID);
        assertThat(logs)
            .as("Logs should contain spanId=<hex>")
            .containsPattern(SPAN_ID);
    }

    /**
     * Test 2: Trace context is logged even for error responses (404)
     */
    @Test
    void shouldLogTraceContextForErrorResponse(CapturedOutput output) {
        String logsBefore = output.getOut();

        restTemplate.getForEntity("/owners/99999", String.class);

        String logs = getLogsDelta(output, logsBefore);

        assertThat(logs)
            .as("Logs should contain traceId for error responses")
            .containsPattern(TRACE_ID);
        assertThat(logs)
            .as("Logs should contain spanId for error responses")
            .containsPattern(SPAN_ID);
    }

    /**
     * Test 3: Trace context is logged for POST requests
     */
    @Test
    void shouldLogTraceContextForPostRequest(CapturedOutput output) {
        String logsBefore = output.getOut();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String validOwnerJson = """
            {
                "firstName": "John",
                "lastName": "Doe",
                "address": "123 Main St",
                "city": "Springfield",
                "telephone": "5551234567"
            }
            """;
        HttpEntity<String> request = new HttpEntity<>(validOwnerJson, headers);

        restTemplate.postForEntity("/owners", request, String.class);

        String logs = getLogsDelta(output, logsBefore);

        assertThat(logs)
            .as("Logs should contain traceId for POST requests")
            .containsPattern(TRACE_ID);
        assertThat(logs)
            .as("Logs should contain spanId for POST requests")
            .containsPattern(SPAN_ID);
    }

    /**
     * Test 4: Provided traceId from headers is propagated and logged.
     * Uses W3C Trace Context format (traceparent header).
     */
    @Test
    void shouldPropagateProvidedTraceId(CapturedOutput output) {
        // Fixed traceId (32 hex) and parentSpanId (16 hex)
        String expectedTraceId = "463ac35c9f6413ad48485a3953bb6124";
        String parentSpanId = "0020000000000001";

        // W3C Trace Context format: version-traceId-parentId-flags
        String traceparent = "00-" + expectedTraceId + "-" + parentSpanId + "-01";

        String logsBefore = output.getOut();

        HttpHeaders headers = new HttpHeaders();
        headers.set("traceparent", traceparent);

        restTemplate.exchange("/owners", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        String logs = getLogsDelta(output, logsBefore);

        assertThat(logs)
            .as("Logs should contain the propagated traceId: " + expectedTraceId)
            .contains("traceId=" + expectedTraceId);
    }

    private String getLogsDelta(CapturedOutput output, String logsBefore) {
        String logsAfter = output.getOut();
        return logsAfter.length() >= logsBefore.length()
            ? logsAfter.substring(logsBefore.length())
            : logsAfter;
    }
}
