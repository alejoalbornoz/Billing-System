package com.billing_client.billing_client.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * Validates a CUIT in two steps, reporting which one failed:
 *   1. Format XX-XXXXXXXX-X
 *   2. Modulo 11 check digit (the last digit)
 */
public class CuitValidator implements ConstraintValidator<Cuit, String> {

    private static final Pattern FORMAT = Pattern.compile("\\d{2}-\\d{8}-\\d");

    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    @Override
    public boolean isValid(String cuit, ConstraintValidatorContext context) {
        if (cuit == null || cuit.isBlank()) {
            return true; // @NotBlank reports missing values
        }

        if (!FORMAT.matcher(cuit).matches()) {
            return reject(context, "CUIT format must be XX-XXXXXXXX-X.");
        }

        if (!hasValidCheckDigit(cuit)) {
            return reject(context, "CUIT check digit is invalid.");
        }

        return true;
    }

    /**
     * Each of the first 10 digits is multiplied by 5,4,3,2,7,6,5,4,3,2 and the products
     * are added up. The check digit is 11 minus the remainder of dividing that sum by 11:
     * 11 becomes 0, and 10 is impossible (AFIP never issues those CUITs).
     */
    private boolean hasValidCheckDigit(String cuit) {
        String digits = cuit.replace("-", "");

        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * WEIGHTS[i];
        }

        int expected = 11 - (sum % 11);
        if (expected == 11) {
            expected = 0;
        }

        return expected != 10 && expected == Character.getNumericValue(digits.charAt(10));
    }

    private boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
