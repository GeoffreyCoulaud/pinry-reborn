package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.NameBounds
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/** A name [NameBounds] accepts, whose blank is Kotlin's and not `@NotBlank`'s; null passes. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NameTextValidator::class])
annotation class NameText(
    val message: String = "must be a name of 1 to ${NameBounds.MAX_LENGTH} characters, not blank",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NameTextValidator : ConstraintValidator<NameText, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || NameBounds.accepts(value)
}
