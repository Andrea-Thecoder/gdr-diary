package it.gdr.diary.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidTextValidator implements ConstraintValidator<ValidText, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || StorableText.isStorable(value);
    }
}
