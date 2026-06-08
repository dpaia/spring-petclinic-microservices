package org.springframework.samples.petclinic.api.application;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.samples.petclinic.api.dto.Visits;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for VisitsServiceClient using WireMock to simulate an external API.
 * This demonstrates how to use WireMock to mock external API dependencies in Spring Boot applications.
 */
class VisitsServiceClientWireMockTest {

    private static final Integer PET_ID = 1;

    @RegisterExtension
    static WireMockExtension wireMockServer = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private VisitsServiceClient visitsServiceClient;

    @BeforeEach
    void setUp() {
        visitsServiceClient = new VisitsServiceClient(WebClient.builder());
        visitsServiceClient.setHostname(wireMockServer.baseUrl() + "/");
    }

    @Test
    void getVisitsForPets_withAvailableVisitsService() {
        // Setup the WireMock stub for the visits service
        wireMockServer.stubFor(get(urlEqualTo("/pets/visits?petId=" + PET_ID))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[{\"id\":5,\"date\":\"2018-11-15\",\"description\":\"test visit\",\"petId\":1}]}")));

        // Call the service client
        Mono<Visits> visits = visitsServiceClient.getVisitsForPets(Collections.singletonList(PET_ID));

        // Verify the response
        assertVisitDescriptionEquals(visits.block(), PET_ID, "test visit");

        // Verify that the expected request was made to the mock server
        wireMockServer.verify(getRequestedFor(urlEqualTo("/pets/visits?petId=" + PET_ID)));
    }

    @Test
    void getVisitsForPets_withMultiplePets() {
        // Setup the WireMock stub for the visits service with multiple pet IDs
        // Use urlPathEqualTo and queryParam for more flexible matching
        wireMockServer.stubFor(get(urlPathEqualTo("/pets/visits"))
                .withQueryParam("petId", equalTo("1,2"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[" +
                                "{\"id\":5,\"date\":\"2018-11-15\",\"description\":\"test visit for pet 1\",\"petId\":1}," +
                                "{\"id\":6,\"date\":\"2018-11-16\",\"description\":\"test visit for pet 2\",\"petId\":2}" +
                                "]}")));

        // Call the service client with multiple pet IDs
        Mono<Visits> visits = visitsServiceClient.getVisitsForPets(java.util.Arrays.asList(1, 2));
        Visits result = visits.block();

        // Verify the response
        assertNotNull(result);
        assertEquals(2, result.items().size());
        assertEquals(1, result.items().get(0).petId());
        assertEquals("test visit for pet 1", result.items().get(0).description());
        assertEquals(2, result.items().get(1).petId());
        assertEquals("test visit for pet 2", result.items().get(1).description());

        // Verify that the expected request was made to the mock server
        // Use urlPathEqualTo and queryParam for verification to handle URL encoding
        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/pets/visits"))
                .withQueryParam("petId", equalTo("1,2")));
    }

    private void assertVisitDescriptionEquals(Visits visits, int petId, String description) {
        assertEquals(1, visits.items().size());
        assertNotNull(visits.items().get(0));
        assertEquals(petId, visits.items().get(0).petId());
        assertEquals(description, visits.items().get(0).description());
    }
}
