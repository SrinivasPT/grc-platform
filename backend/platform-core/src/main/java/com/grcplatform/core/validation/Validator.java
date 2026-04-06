package com.grcplatform.core.validation;

import com.grcplatform.core.exception.ValidationException;

/**
 * Composable, single-purpose validator for a command type.
 *
 * <p>
 * Structural validation (null, size, format) belongs to Jakarta Bean Validation annotations on
 * command records. Business-rule validation belongs here: uniqueness constraints, cross-field
 * rules, org-specific policy checks, etc.
 *
 * <p>
 * Validators are composed via {@link #and(Validator)} and wired as {@code List<Validator<C>>} in
 * each {@code *SliceConfig} — making the full validation surface visible in one place.
 *
 * <pre>{@code
 * // In SliceConfig:
 * List<Validator<CreateOrgUnitCommand>> validators = List.of(cmd -> {
 *     if (orgUnitRepository.findByOrgIdAndCode(orgId, cmd.code()).isPresent())
 *         throw new ValidationException("code", "Code already exists");
 * }, cmd -> {
 *     if (cmd.displayOrder() < 0)
 *         throw new ValidationException("displayOrder", "Must be non-negative");
 * });
 * }</pre>
 *
 * @param <C> the command type being validated
 */
@FunctionalInterface
public interface Validator<C> {

    /**
     * Validates the command. Throws {@link ValidationException} if validation fails.
     *
     * @param command the command to validate
     * @throws ValidationException if the command is invalid
     */
    void validate(C command);

    /**
     * Returns a composed validator that runs {@code this} validator and then {@code other}. Both
     * must pass; if {@code this} fails, {@code other} is not called.
     *
     * @param other the next validator to apply
     * @return a composed validator
     */
    default Validator<C> and(Validator<C> other) {
        return cmd -> {
            this.validate(cmd);
            other.validate(cmd);
        };
    }
}
