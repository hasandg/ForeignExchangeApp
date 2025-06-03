package com.hasandag.exchange.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;


public class CurrencyCodeValidator implements ConstraintValidator<CurrencyCode, String> {

    private static final String CURRENCY_CODE_PATTERN = "^[A-Z]{3}$";

    @Override
    public void initialize(CurrencyCode constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        return value.matches(CURRENCY_CODE_PATTERN);
    }
} 