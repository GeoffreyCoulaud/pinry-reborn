package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/** A name [PersonName]'s factory accepts, whose blank is Kotlin's and not `@NotBlank`'s; null passes. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PersonNameTextValidator::class])
annotation class PersonNameText(
    val message: String = "must be a name of 1 to ${PersonName.MAX_LENGTH} characters, not blank",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class PersonNameTextValidator : ConstraintValidator<PersonNameText, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || PersonName.parse(value) != null
}
