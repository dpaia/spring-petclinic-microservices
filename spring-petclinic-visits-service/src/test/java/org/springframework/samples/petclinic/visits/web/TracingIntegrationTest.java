package org.springframework.samples.petclinic.visits.web;

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

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests to verify trace context logging in visits-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class TracingIntegrationTest {

    private static final Pattern TRACE_ID = Pattern.compile("traceId=[0-9a-fA-F]{16,32}");
    private static final Pattern SPAN_ID  = Pattern.compile("spanId=[0-9a-fA-F]{16}");
    private static final Pattern TRACE_ID_VALUE = Pattern.compile("traceId=([0-9a-fA-F]{16,32})");

    @Autowired
    TestRestTemplate restTemplate;

    /**
     * Test 1: Logs contain traceId=<hex> and spanId=<hex> for GET requests
     */
    @Test
    void shouldLogTraceIdAndSpanIdWithCorrectFormat(CapturedOutput output) {
        String logsBefore = output.getOut();

        restTemplate.getForEntity("/pets/visits?petId=1", String.class);

        String logs = getLogsDelta(output, logsBefore);

        assertThat(logs)
            .as("Logs should contain traceId=<hex>")
            .containsPattern(TRACE_ID);
        assertThat(logs)
            .as("Logs should contain spanId=<hex>")
            .containsPattern(SPAN_ID);
    }

    /**
     * Test 2: Trace context is logged for POST requests
     */
    @Test
    void shouldLogTraceContextForPostRequest(CapturedOutput output) {
        String logsBefore = output.getOut();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"petId\":1,\"description\":\"test\"}", headers);

        restTemplate.postForEntity("/owners/1/pets/1/visits", request, String.class);

        String logs = getLogsDelta(output, logsBefore);

        assertThat(logs)
            .as("Logs should contain traceId for POST requests")
            .containsPattern(TRACE_ID);
        assertThat(logs)
            .as("Logs should contain spanId for POST requests")
            .containsPattern(SPAN_ID);
    }

    /**
     * Test 3: All logs within a single request should have the same traceId.
     */
    @Test
    void shouldHaveConsistentTraceIdAcrossMultipleLogs(CapturedOutput output) {
        String logsBefore = output.getOut();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"petId\":1,\"description\":\"test\"}", headers);
        restTemplate.postForEntity("/owners/1/pets/1/visits", request, String.class);

        String logs = getLogsDelta(output, logsBefore);

        // Extract all traceIds from logs
        Matcher matcher = TRACE_ID_VALUE.matcher(logs);

        Set<String> traceIds = new HashSet<>();
        while (matcher.find()) {
            traceIds.add(matcher.group(1));
        }

        // Must have at least one traceId logged
        assertThat(traceIds)
            .as("Logs should contain at least one traceId")
            .isNotEmpty();

        // All traceIds should be the same (only one unique value)
        assertThat(traceIds)
            .as("All logs within one request should have the same traceId")
            .hasSize(1);
    }

    /**
     * Test 4: Provided traceId from headers is propagated and logged.
     * Uses W3C Trace Context format (traceparent header).
     */
    @Test
    void shouldPropagateProvidedTraceId(CapturedOutput output) {
        String expectedTraceId = "463ac35c9f6413ad48485a3953bb6124";
        String parentSpanId = "0020000000000001";

        // W3C Trace Context format: version-traceId-parentId-flags
        String traceparent = "00-" + expectedTraceId + "-" + parentSpanId + "-01";

        String logsBefore = output.getOut();

        HttpHeaders headers = new HttpHeaders();
        headers.set("traceparent", traceparent);

        restTemplate.exchange("/pets/visits?petId=1", HttpMethod.GET, new HttpEntity<>(headers), String.class);

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
