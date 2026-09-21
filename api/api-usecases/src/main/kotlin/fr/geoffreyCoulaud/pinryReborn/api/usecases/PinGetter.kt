package fr.geoffreyCoulaud.pinryReborn.api.usecases

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.PinSortStrategy
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.PinRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinRetrievalPermissionError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.PinRetrievalPinDoesNotExistError
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.SearchEmptyQueryError
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class PinGetter(
    private val pinRepository: PinRepositoryInterface,
) {
    fun getPinForUser(
        reader: User,
        pinId: UUID,
    ): Pin {
        val pin = pinRepository.findPinById(id = pinId) ?: throw PinRetrievalPinDoesNotExistError()
        if (pin.author != reader) throw PinRetrievalPermissionError()
        return pin
    }

    fun listPinsPaginatedForUser(
        reader: User,
        cursor: Cursor?,
        pageSize: Int,
        sort: PinSortStrategy,
        query: String? = null,
    ): Page<Pin> {
        if (query != null && query.isBlank()) throw SearchEmptyQueryError()
        if (cursor != null) {
            // If a cursor is provided, check that it points to a user-readable pin
            getPinForUser(pinId = cursor.pivotId, reader = reader)
        }
        return pinRepository.findPinsForUser(
            reader = reader,
            cursor = cursor,
            pageSize = pageSize.coerceIn(1, MAX_PAGE_SIZE),
            sortStrategy = sort,
            query = query,
        )
    }

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
