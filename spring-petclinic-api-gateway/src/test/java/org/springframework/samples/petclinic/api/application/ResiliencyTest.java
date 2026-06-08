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
import org.springframework.samples.petclinic.api.dto.OwnerDetails;
import org.springframework.samples.petclinic.api.dto.Visits;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class to verify timeout and fallback mechanisms for service clients
 */
class ResiliencyTest {

    private WireMockServer wireMockServer;
    private CustomersServiceClient customersServiceClient;
    private VisitsServiceClient visitsServiceClient;
    private ReactiveResilience4JCircuitBreakerFactory circuitBreakerFactory;

    private static final int OWNER_ID = 1;
    private static final String OWNER_JSON = "{\"id\":1,\"firstName\":\"John\",\"lastName\":\"Doe\",\"address\":\"123 Main St\",\"city\":\"Anytown\",\"telephone\":\"555-1234\",\"pets\":[{\"id\":1,\"name\":\"Fluffy\",\"birthDate\":\"2020-01-01\",\"type\":{\"id\":1,\"name\":\"cat\"},\"visits\":[]}]}";
    private static final String VISITS_JSON = "{\"items\":[{\"id\":1,\"date\":\"2023-01-01\",\"description\":\"Annual checkup\",\"petId\":1}]}";

    @BeforeEach
    void setUp() {
        // Set up WireMock server
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        // Set up circuit breaker factory
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        TimeLimiterRegistry timeLimiterRegistry = TimeLimiterRegistry.ofDefaults();
        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
        circuitBreakerFactory = new ReactiveResilience4JCircuitBreakerFactory(
            circuitBreakerRegistry, timeLimiterRegistry, properties);

        // Set up clients with direct URLs (no service discovery)
        WebClient.Builder webClientBuilder = WebClient.builder();

        customersServiceClient = new CustomersServiceClient(webClientBuilder, circuitBreakerFactory);
        customersServiceClient.setHostname(wireMockServer.baseUrl() + "/");
        customersServiceClient.setTimeout(Duration.ofMillis(500)); // Short timeout for testing

        visitsServiceClient = new VisitsServiceClient(webClientBuilder);
        visitsServiceClient.setHostname(wireMockServer.baseUrl() + "/");
        visitsServiceClient.setTimeout(Duration.ofMillis(500)); // Short timeout for testing
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testCustomersServiceNormalResponse() {
        // Arrange
        wireMockServer.stubFor(get(urlEqualTo("/owners/" + OWNER_ID))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(OWNER_JSON)));

        // Act & Assert
        StepVerifier.create(customersServiceClient.getOwner(OWNER_ID))
            .assertNext(owner -> {
                assertEquals(OWNER_ID, owner.id());
                assertEquals("John", owner.firstName());
                assertEquals("Doe", owner.lastName());
            })
            .verifyComplete();
    }

    @Test
    void testCustomersServiceTimeout() {
        // Arrange
        wireMockServer.stubFor(get(urlEqualTo("/owners/" + OWNER_ID))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(OWNER_JSON)
                .withFixedDelay(1000))); // Delay longer than timeout

        // Act & Assert - Should trigger fallback
        StepVerifier.create(customersServiceClient.getOwner(OWNER_ID))
            .assertNext(owner -> {
                assertEquals(OWNER_ID, owner.id());
                assertEquals("Unknown", owner.firstName());
                assertEquals("Owner", owner.lastName());
                assertTrue(owner.pets().isEmpty());
            })
            .verifyComplete();
    }

    @Test
    void testVisitsServiceNormalResponse() {
        // Arrange
        wireMockServer.stubFor(get(urlPathMatching("/pets/visits.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(VISITS_JSON)));

        // Act & Assert
        StepVerifier.create(visitsServiceClient.getVisitsForPets(List.of(1)))
            .assertNext(visits -> {
                assertEquals(1, visits.items().size());
                assertEquals("Annual checkup", visits.items().get(0).description());
            })
            .verifyComplete();
    }

    @Test
    void testVisitsServiceTimeout() {
        // Arrange
        wireMockServer.stubFor(get(urlPathMatching("/pets/visits.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(VISITS_JSON)
                .withFixedDelay(1000))); // Delay longer than timeout

        // Act & Assert - Should timeout and throw an exception
        StepVerifier.create(visitsServiceClient.getVisitsForPets(List.of(1)))
            .expectError() // Accept any error, as it could be TimeoutException or WebClientResponseException
            .verify();
    }

    @Test
    void testVisitsServiceWithCircuitBreaker() {
        // Arrange
        wireMockServer.stubFor(get(urlPathMatching("/pets/visits.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(VISITS_JSON)
                .withFixedDelay(1000))); // Delay longer than timeout

        // Create a Mono with circuit breaker
        Mono<Visits> visitsWithFallback = visitsServiceClient.getVisitsForPets(List.of(1))
            .transform(it -> {
                var cb = circuitBreakerFactory.create("testCircuitBreaker");
                return cb.run(it, throwable -> Mono.just(new Visits(Collections.emptyList())));
            });

        // Act & Assert - Should use fallback
        StepVerifier.create(visitsWithFallback)
            .assertNext(visits -> {
                assertTrue(visits.items().isEmpty());
            })
            .verifyComplete();
    }
}
