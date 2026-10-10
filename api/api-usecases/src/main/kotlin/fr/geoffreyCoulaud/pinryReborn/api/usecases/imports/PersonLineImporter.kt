package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportIssueKind
import fr.geoffreyCoulaud.pinryReborn.api.domain.imports.ArchiveLine
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PersonCreator

/** One attempt's walk of `persons.jsonl` (specification 2026-10-10, decision F). */
internal class PersonLineImporter(
    private val personCreator: PersonCreator,
    private val user: User,
    private val clamp: ImportInstantClamp,
) {
    // Every id a line within the bound carried, refused or not, so it holds at most that many.
    private val usedIds = mutableSetOf<String>()

    /** Finds or creates the line's person by its name and addresses, or reports why the line is refused. */
    fun import(line: ArchiveLine<ImportedPersonLine>, report: (UserDataImportIssueKind, String?, String?) -> Unit) {
        val person = line.value
        val fault = person?.let { fault(line.line, it) }
        when {
            person == null -> report(UserDataImportIssueKind.LINE_MALFORMED, null, line.failure)
            fault != null -> report(UserDataImportIssueKind.FIELD_INVALID, person.id, fault)
            else -> {
                val reference = person.toReference()
                personCreator.findOrCreate(reference.name, reference.urls, user, clamp.clamp(person.createdAt))
            }
        }
    }

    private fun fault(line: Int, person: ImportedPersonLine): String? =
        when {
            line > MAX_LINES -> "persons.jsonl holds more than $MAX_LINES lines"
            person.id.isBlank() || person.id.length > MAX_ID_LENGTH -> ID_FAULT
            !usedIds.add(person.id) -> "id is used by an earlier line"
            else -> personFault(person.name, person.urls)
        }

    companion object {
        const val MAX_LINES = 100_000
        const val MAX_ID_LENGTH = 200
        private const val ID_FAULT = "id is blank or longer than $MAX_ID_LENGTH characters"
        private const val URL_FIELD = "a person's address"
        private const val NAME_FAULT = "a person's name is blank or longer than ${PersonName.MAX_LENGTH} characters"

        /** A person as a person line or a pin line names it, by its name and its addresses. */
        fun personFault(name: String, urls: List<String>): String? =
            (if (PersonName.parse(name) == null) NAME_FAULT else null)
                ?: ImportFieldBounds.personFault(urls)
                ?: urls.firstNotNullOfOrNull { ImportFieldBounds.httpAddressFault(URL_FIELD, it) }
    }
}
