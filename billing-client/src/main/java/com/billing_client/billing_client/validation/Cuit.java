package com.billing_client.billing_client.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Argentine CUIT: format XX-XXXXXXXX-X and a valid modulo 11 check digit.
 * Null and blank values pass — combine with @NotBlank when the field is required.
 */
@Documented
@Constraint(validatedBy = CuitValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Cuit {

    String message() default "CUIT is invalid.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
