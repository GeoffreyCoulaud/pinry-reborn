package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Username
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/** An account name [Username]'s factory accepts; null passes. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [AccountNameValidator::class])
annotation class AccountName(
    val message: String =
        "must be ${Username.MIN_LENGTH} to ${Username.MAX_LENGTH} characters of letters, digits, '.', '_' or '-'",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class AccountNameValidator : ConstraintValidator<AccountName, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || Username.parse(value) != null
}
