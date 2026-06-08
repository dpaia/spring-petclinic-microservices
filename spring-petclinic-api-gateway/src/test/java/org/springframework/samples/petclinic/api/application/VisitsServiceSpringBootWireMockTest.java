package org.springframework.samples.petclinic.api.application;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.samples.petclinic.api.dto.Visits;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Spring Boot integration test for VisitsServiceClient using WireMock.
 * This demonstrates how to use WireMock with Spring Boot's test framework
 * to mock external API dependencies in integration tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWireMock(port = 0) // Use a random port
@TestPropertySource(properties = {
    "spring.cloud.discovery.enabled=false",
    "spring.cloud.config.enabled=false",
    "spring.cloud.loadbalancer.enabled=false"
})
class VisitsServiceSpringBootWireMockTest {

    private static final Integer PET_ID = 1;

    @Autowired
    private VisitsServiceClient visitsServiceClient;

    @Value("${wiremock.server.port}")
    private int wireMockPort;

    @BeforeEach
    void setUp() {
        // Reset WireMock to ensure clean state for each test
        WireMock.reset();

        // Configure the client to use the WireMock server with a direct URL
        // This bypasses the service discovery and load balancing
        visitsServiceClient.setHostname("http://localhost:" + wireMockPort + "/");

        // Log the WireMock server URL for debugging
        System.out.println("[DEBUG_LOG] WireMock server URL: http://localhost:" + wireMockPort + "/");
    }

    @Test
    void getVisitsForPets_withAvailableVisitsService() {
        // Setup the WireMock stub for the visits service
        stubFor(get(urlEqualTo("/pets/visits?petId=" + PET_ID))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[{\"id\":5,\"date\":\"2018-11-15\",\"description\":\"test visit\",\"petId\":1}]}")));

        // Call the service client
        Mono<Visits> visits = visitsServiceClient.getVisitsForPets(Collections.singletonList(PET_ID));

        // Verify the response
        assertVisitDescriptionEquals(visits.block(), PET_ID, "test visit");

        // Verify that the expected request was made to the mock server
        verify(getRequestedFor(urlEqualTo("/pets/visits?petId=" + PET_ID)));
    }

    @Test
    void getVisitsForPets_withErrorResponse() {
        // Setup the WireMock stub for an error response
        stubFor(get(urlEqualTo("/pets/visits?petId=" + PET_ID))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal Server Error\"}")));

        // Call the service client and handle the error
        // In a real application, you would have error handling logic
        // For this example, we'll just catch the exception
        try {
            Mono<Visits> visits = visitsServiceClient.getVisitsForPets(Collections.singletonList(PET_ID));
            visits.block(); // This should throw an exception
        } catch (Exception e) {
            // Expected exception
            System.out.println("Caught expected exception: " + e.getMessage());
        }

        // Verify that the expected request was made to the mock server
        verify(getRequestedFor(urlEqualTo("/pets/visits?petId=" + PET_ID)));
    }

    private void assertVisitDescriptionEquals(Visits visits, int petId, String description) {
        assertEquals(1, visits.items().size());
        assertNotNull(visits.items().get(0));
        assertEquals(petId, visits.items().get(0).petId());
        assertEquals(description, visits.items().get(0).description());
    }
}
