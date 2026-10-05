package fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinDuplicateListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinDuplicateOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinListOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.PinOutputDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.PinMapper.toDto
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicate
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinDuplicates
import fr.geoffreyCoulaud.pinryReborn.api.usecases.ResolvePinMediaState
import jakarta.enterprise.context.ApplicationScoped

/**
 * Every payload that carries a pin, built here so none forgets the image state or the duplicates
 * flag: a null `media` then means the pin has none, never that the response did not look.
 */
@ApplicationScoped
class PinResponses(
    private val resolvePinMediaState: ResolvePinMediaState,
    private val pinDuplicates: PinDuplicates,
) {
    fun pin(pin: Pin): PinOutputDto = mapped(listOf(pin)).single()

    fun page(page: Page<Pin>): PinListOutputDto =
        page.toDto(resolvePinMediaState.statesFor(page.items), pinDuplicates.pendingAmong(page.items))

    fun duplicates(duplicates: List<PinDuplicate>): PinDuplicateListOutputDto {
        val pins = mapped(duplicates.map { it.pin })
        return PinDuplicateListOutputDto(
            duplicates.zip(pins) { duplicate, pin -> PinDuplicateOutputDto(pin, duplicate.rejected) },
        )
    }

    fun duplicate(duplicate: PinDuplicate): PinDuplicateOutputDto = duplicates(listOf(duplicate)).duplicates.single()

    // The media states and the duplicates flags, each read once for all of [pins].
    private fun mapped(pins: List<Pin>): List<PinOutputDto> {
        val states = resolvePinMediaState.statesFor(pins)
        val pending = pinDuplicates.pendingAmong(pins)
        return pins.map { it.toDto(states, pending) }
    }
}
