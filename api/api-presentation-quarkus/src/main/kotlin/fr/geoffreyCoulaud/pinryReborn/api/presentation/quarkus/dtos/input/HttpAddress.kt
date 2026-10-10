package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.input

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/** An address [HttpUrl]'s factory accepts; null passes, the type deciding whether it may be absent. */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [HttpAddressValidator::class])
annotation class HttpAddress(
    val message: String = "must be an absolute http or https address",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class HttpAddressValidator : ConstraintValidator<HttpAddress, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || HttpUrl.parse(value) != null
}
