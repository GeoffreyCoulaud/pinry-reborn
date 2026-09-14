package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Cursor
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Page
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.UserDataImport
import fr.geoffreyCoulaud.pinryReborn.api.domain.repositories.UserDataImportRepositoryInterface
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinGetter
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Reads user data imports (spec §6). [get] is where [UserDataImportIssueLister] and
 * [UserDataImportCanceller] send their existence and ownership check, so it exists once.
 */
@ApplicationScoped
class UserDataImportGetter(
    private val repository: UserDataImportRepositoryInterface,
) {
    fun get(user: User, importId: UUID): UserDataImport = repository.findOwned(user, importId)

    /** [pageSize] is clamped as [PinGetter] clamps it: at zero the helper answers a page with no cursor. */
    fun list(user: User, cursor: Cursor?, pageSize: Int): Page<UserDataImport> =
        repository.findAllForUser(user.id, cursor, pageSize.coerceIn(1, PinGetter.MAX_PAGE_SIZE))
}
