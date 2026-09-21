package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ResolvePinImageState
import jakarta.enterprise.context.ApplicationScoped

/**
 * Every payload that carries a pin, built here so none forgets the image state: a null `image` then
 * means the pin has none, never that the response did not look.
 */
@ApplicationScoped
class PinResponses(private val resolvePinImageState: ResolvePinImageState) {
    fun pin(pin: Pin): PinOutputDto = pin.toDto(resolvePinImageState.statesFor(listOf(pin)))

    fun page(page: Page<Pin>): PinListOutputDto = page.toDto(resolvePinImageState.statesFor(page.items))
}
