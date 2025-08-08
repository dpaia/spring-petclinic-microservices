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
package org.springframework.samples.petclinic.visits.web;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import io.micrometer.core.annotation.Timed;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.visits.model.Visit;
import org.springframework.samples.petclinic.visits.model.VisitRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 * @author Maciej Szarlinski
 * @author Ramazan Sakin
 */
@RestController
@Timed("petclinic.visit")
class VisitResource {

    private static final Logger log = LoggerFactory.getLogger(VisitResource.class);

    private final VisitRepository visitRepository;
    private final Tracer tracer;

    VisitResource(VisitRepository visitRepository, Tracer tracer) {
        this.visitRepository = visitRepository;
        this.tracer = tracer;
    }

    @PostMapping("owners/*/pets/{petId}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    public Visit create(
        @Valid @RequestBody Visit visit,
        @PathVariable("petId") @Min(1) int petId) {

        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            String traceId = currentSpan.context().traceId();
            String spanId = currentSpan.context().spanId();
            log.info("Creating visit for pet ID: {}, traceId: {}, spanId: {}", petId, traceId, spanId);
        } else {
            log.info("Creating visit for pet ID: {}, no current span found", petId);
        }

        visit.setPetId(petId);
        log.info("Saving visit {}", visit);
        return visitRepository.save(visit);
    }

    @GetMapping("owners/*/pets/{petId}/visits")
    public List<Visit> read(@PathVariable("petId") @Min(1) int petId) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            String traceId = currentSpan.context().traceId();
            String spanId = currentSpan.context().spanId();
            log.info("Finding visits for pet ID: {}, traceId: {}, spanId: {}", petId, traceId, spanId);
        } else {
            log.info("Finding visits for pet ID: {}, no current span found", petId);
        }
        return visitRepository.findByPetId(petId);
    }

    @GetMapping("pets/visits")
    public Visits read(@RequestParam("petId") List<Integer> petIds) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            String traceId = currentSpan.context().traceId();
            String spanId = currentSpan.context().spanId();
            log.info("Finding visits for pet IDs: {}, traceId: {}, spanId: {}", petIds, traceId, spanId);
        } else {
            log.info("Finding visits for pet IDs: {}, no current span found", petIds);
        }
        final List<Visit> byPetIdIn = visitRepository.findByPetIdIn(petIds);
        return new Visits(byPetIdIn);
    }

    record Visits(
        List<Visit> items
    ) {
    }
}
