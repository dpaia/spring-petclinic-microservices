package org.springframework.samples.petclinic.customers.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link ValidAddressValidator}
 */
@ExtendWith(MockitoExtension.class)
class ValidAddressValidatorTest {

    private ValidAddressValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new ValidAddressValidator();
    }

    @Test
    void shouldValidateCorrectAddress() {
        // Valid addresses with street number followed by street name
        assertTrue(validator.isValid("123 Main Street", context));
        assertTrue(validator.isValid("45 Park Avenue", context));
        assertTrue(validator.isValid("67 Oak Street, Apt 2B", context));
        assertTrue(validator.isValid("890 Pine Road, Suite 100", context));
    }

    @Test
    void shouldNotValidateIncorrectAddress() {
        // Invalid addresses without street number or street name
        assertFalse(validator.isValid("Main Street", context));
        assertFalse(validator.isValid("123", context));
        assertFalse(validator.isValid("123,", context));
        assertFalse(validator.isValid("No street number here", context));
    }

    @Test
    void shouldHandleNullAndEmptyValues() {
        // Null and empty values should be considered valid (handled by @NotBlank)
        assertTrue(validator.isValid(null, context));
        assertTrue(validator.isValid("", context));
        assertTrue(validator.isValid("   ", context));
    }
}
