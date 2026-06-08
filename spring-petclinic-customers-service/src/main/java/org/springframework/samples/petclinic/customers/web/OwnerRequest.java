package org.springframework.samples.petclinic.customers.web;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import org.springframework.samples.petclinic.customers.validation.ValidAddress;

public record OwnerRequest(@NotBlank String firstName,
                           @NotBlank String lastName,
                           @NotBlank @ValidAddress String address,
                           @NotBlank String city,
                           @NotBlank
                           @Digits(fraction = 0, integer = 12)
                           String telephone
) {
}
