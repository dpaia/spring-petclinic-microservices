package org.springframework.samples.petclinic.api.application;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomersServiceClientIntegrationTest {

    private static final Integer OWNER_ID = 1;
    private static final String OWNER_RESOURCE_JSON = "{\"id\":1,\"firstName\":\"John\",\"lastName\":\"Doe\",\"address\":\"123 Main St\",\"city\":\"Anytown\",\"telephone\":\"555-1234\",\"pets\":[]}";

    private CustomersServiceClient customersServiceClient;
    private WireMockServer wireMockServer;
    private ReactiveResilience4JCircuitBreakerFactory circuitBreakerFactory;

    @BeforeEach
    void setUp() {
        // Set up WireMock server
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        // Set up circuit breaker factory with custom configuration
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();

        // Create circuit breaker factory
        circuitBreakerFactory = new ReactiveResilience4JCircuitBreakerFactory(
            circuitBreakerRegistry, timeLimiterRegistry, properties);

        // Configure the circuit breaker to open after 3 failures
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig circuitBreakerConfig =
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMillis(1000))
                .permittedNumberOfCallsInHalfOpenState(2)
                .minimumNumberOfCalls(3)
                .build();

        // Apply the configuration to the factory for the specific circuit breaker name
        circuitBreakerFactory.configure(
            builder -> builder.circuitBreakerConfig(circuitBreakerConfig),
            "customersServiceCircuitBreaker");

        // Set up client
        customersServiceClient = new CustomersServiceClient(WebClient.builder(), circuitBreakerFactory);
        customersServiceClient.setHostname(wireMockServer.baseUrl() + "/");
        customersServiceClient.setTimeout(Duration.ofMillis(500)); // Short timeout for testing
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void getOwner_withNormalResponse_shouldReturnOwner() {
        // Arrange
        stubFor(get(urlEqualTo("/owners/" + OWNER_ID))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(OWNER_RESOURCE_JSON)));

        // Act & Assert
        StepVerifier.create(customersServiceClient.getOwner(OWNER_ID))
            .assertNext(owner -> {
                assertEquals(OWNER_ID, owner.id());
                assertEquals("John", owner.firstName());
                assertEquals("Doe", owner.lastName());
            })
            .verifyComplete();

        verify(getRequestedFor(urlEqualTo("/owners/" + OWNER_ID)));
    }

    @Test
    void getOwner_withDelay_shouldTriggerTimeout() {
        // Arrange
        stubFor(get(urlEqualTo("/owners/" + OWNER_ID))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(OWNER_RESOURCE_JSON)
                .withFixedDelay(1000))); // Delay longer than timeout

        // Act & Assert
        StepVerifier.create(customersServiceClient.getOwner(OWNER_ID))
            .assertNext(owner -> {
                assertEquals(OWNER_ID, owner.id());
                assertEquals("Unknown", owner.firstName());
                assertEquals("Owner", owner.lastName());
                assertTrue(owner.pets().isEmpty());
            })
            .verifyComplete();

        verify(getRequestedFor(urlEqualTo("/owners/" + OWNER_ID)));
    }

    @Test
    void getOwner_withServerError_shouldReturnFallback() {
        // Arrange
        stubFor(get(urlEqualTo("/owners/" + OWNER_ID))
            .willReturn(aResponse()
                .withStatus(503)
                .withStatusMessage("Service Unavailable")));

        // Act & Assert
        StepVerifier.create(customersServiceClient.getOwner(OWNER_ID))
            .assertNext(owner -> {
                assertEquals(OWNER_ID, owner.id());
                assertEquals("Unknown", owner.firstName());
                assertEquals("Owner", owner.lastName());
                assertTrue(owner.pets().isEmpty());
            })
            .verifyComplete();

        verify(getRequestedFor(urlEqualTo("/owners/" + OWNER_ID)));
    }

    @Test
    void getOwner_withCircuitOpen_shouldReturnFallbackImmediately() {
        // Arrange - Force circuit to open by making multiple failing calls
        stubFor(get(urlEqualTo("/owners/" + OWNER_ID))
            .willReturn(aResponse()
                .withStatus(503)
                .withStatusMessage("Service Unavailable")));

        // Make several calls to open the circuit
        // Use StepVerifier to make the calls and verify the fallback response
        for (int i = 0; i < 3; i++) {
            StepVerifier.create(customersServiceClient.getOwner(OWNER_ID))
                .assertNext(owner -> {
                    assertEquals(OWNER_ID, owner.id());
                    assertEquals("Unknown", owner.firstName());
                    assertEquals("Owner", owner.lastName());
                    assertTrue(owner.pets().isEmpty());
                })
                .verifyComplete();
        }

        // Verify that the expected number of requests were made
        verify(exactly(3), getRequestedFor(urlEqualTo("/owners/" + OWNER_ID)));

        // Reset the stub to return a normal response
        reset();
        stubFor(get(urlEqualTo("/owners/" + OWNER_ID))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(OWNER_RESOURCE_JSON)));

        // Act & Assert - Even though the service is now "healthy", the circuit is open
        // and should return the fallback without making a request to the service
        StepVerifier.create(customersServiceClient.getOwner(OWNER_ID))
            .assertNext(owner -> {
                assertEquals(OWNER_ID, owner.id());
                assertEquals("Unknown", owner.firstName());
                assertEquals("Owner", owner.lastName());
                assertTrue(owner.pets().isEmpty());
            })
            .verifyComplete();

        // Verify that no additional requests were made (circuit is open)
        verify(exactly(0), getRequestedFor(urlEqualTo("/owners/" + OWNER_ID)));
    }
}
