package org.springframework.samples.petclinic.customers.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validator implementation for the {@link ValidAddress} constraint.
 * Validates that an address contains at least a street number followed by a street name.
 */
public class ValidAddressValidator implements ConstraintValidator<ValidAddress, String> {

    // Pattern to match a street number followed by a street name
    // Matches: digits followed by whitespace followed by at least one word character
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("\\d+\\s+\\w+.*");

    @Override
    public void initialize(ValidAddress constraintAnnotation) {
        // No initialization needed
    }

    @Override
    public boolean isValid(String address, ConstraintValidatorContext context) {
        // Null values are handled by @NotNull annotation if needed
        if (address == null || address.trim().isEmpty()) {
            return true;
        }

        // Check if the address matches the required pattern
        return ADDRESS_PATTERN.matcher(address).matches();
    }
}
