/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.api.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.samples.petclinic.api.dto.OwnerDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.function.Function;

/**
 * @author Maciej Szarlinski
 */
@Component
public class CustomersServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CustomersServiceClient.class);
    private static final String CIRCUIT_BREAKER_NAME = "customersServiceCircuitBreaker";

    private final WebClient.Builder webClientBuilder;
    private final ReactiveCircuitBreakerFactory circuitBreakerFactory;

    // Could be changed for testing purpose
    private String hostname = "http://customers-service/";
    private Duration timeout = Duration.ofSeconds(1);

    public CustomersServiceClient(WebClient.Builder webClientBuilder, ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.webClientBuilder = webClientBuilder;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public Mono<OwnerDetails> getOwner(final int ownerId) {
        return webClientBuilder.build().get()
            .uri(hostname + "owners/{ownerId}", ownerId)
            .retrieve()
            .bodyToMono(OwnerDetails.class)
            .timeout(timeout)
            .transform(it -> {
                ReactiveCircuitBreaker circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_NAME);
                return circuitBreaker.run(it, throwable -> {
                    log.error("Error retrieving owner with id {}: {}", ownerId, throwable.getMessage());
                    return fallbackOwner(ownerId);
                });
            });
    }

    private Mono<OwnerDetails> fallbackOwner(int ownerId) {
        log.info("Returning fallback owner for id: {}", ownerId);
        return Mono.just(OwnerDetails.OwnerDetailsBuilder.anOwnerDetails()
            .id(ownerId)
            .firstName("Unknown")
            .lastName("Owner")
            .address("Not available")
            .city("Not available")
            .telephone("Not available")
            .pets(Collections.emptyList())
            .build());
    }

    void setHostname(String hostname) {
        this.hostname = hostname;
    }

    void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
