package com.billing_client.billing_client.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("@Cuit Validation Tests")
class CuitValidatorTest {

    private static Validator validator;

    record TaxpayerHolder(@Cuit String cuit) {}

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {"30-71234567-1", "20-12345678-6", "20-10000013-0"})
    @DisplayName("Should accept CUITs with a valid check digit")
    void shouldAcceptValidCuits(String cuit) {
        assertThat(validate(cuit)).isEmpty();
    }

    @Test
    @DisplayName("Should leave null and blank values to @NotBlank")
    void shouldIgnoreNullAndBlank() {
        assertThat(validate(null)).isEmpty();
        assertThat(validate("  ")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"30712345671", "30-7123456-71", "ab-cdefghij-k", "30-71234567-12"})
    @DisplayName("Should report a format error when the CUIT is not XX-XXXXXXXX-X")
    void shouldRejectInvalidFormat(String cuit) {
        assertThat(validate(cuit))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("CUIT format must be XX-XXXXXXXX-X.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"30-71234567-0", "20-12345678-9", "20-10000013-1"})
    @DisplayName("Should report a check digit error when the last digit is wrong")
    void shouldRejectWrongCheckDigit(String cuit) {
        assertThat(validate(cuit))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("CUIT check digit is invalid.");
    }

    @Test
    @DisplayName("Should reject every digit when the check digit would be 10 (AFIP never issues those)")
    void shouldRejectCuit_whenCheckDigitWouldBeTen() {
        for (int digit = 0; digit <= 9; digit++) {
            assertThat(validate("20-10000005-" + digit)).hasSize(1);
        }
    }

    private Set<ConstraintViolation<TaxpayerHolder>> validate(String cuit) {
        return validator.validate(new TaxpayerHolder(cuit));
    }
}
