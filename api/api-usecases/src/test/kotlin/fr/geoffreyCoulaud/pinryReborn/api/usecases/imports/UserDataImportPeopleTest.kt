package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportIssueKind
import io.mockk.every
import io.mockk.verify
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A pin line's publisher, creators and publication instant (the spec's decision D). Split off for `LargeClass`. */
internal class UserDataImportPeopleTest : UserDataImportRunnerFixtures() {
    private val savedPersons = mutableListOf<Person>()

    @Test
    fun `Given a pin line crediting people, Then each is found or created at the import instant, its date unclamped`() {
        // Given: the creator is already the account's, the publisher is not
        val existing =
            Person(
                id = randomUUID(),
                author = user,
                name = "Ada",
                urls = listOf("https://a.example"),
                createdAt = accountCreatedAt,
            )
        val line =
            aPin()
                .copy(
                    publisher = ImportedPerson("Studio", listOf("https://studio.example")),
                    creators = listOf(ImportedPerson("Ada", listOf("https://a.example"))),
                    publishedAt = beforeAccount,
                )
        stubWalk(FakeArchiveSource(manifest = aManifest(), pins = listOf(TestLine(1, line)), media = everyMedium))
        stubMediaPath()
        stubPersons(existing)

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        val created = savedPins.single()
        assertEquals("Studio", created.publisher?.name)
        assertEquals(now, created.publisher?.createdAt)
        assertEquals(listOf(existing), created.creators)
        assertEquals(beforeAccount, created.publishedAt)
        assertEquals(1, savedPersons.size)
    }

    @Test
    fun `Given people past their bounds, Then each line is reported invalid and no pin or person is created`() {
        // Given: one line per bound of a person, then the creator count
        val withAddress = { url: String -> aPin().copy(publisher = ImportedPerson("Ada", listOf(url))) }
        val lines =
            listOf(
                aPin().copy(publisher = ImportedPerson(" ", emptyList())),
                aPin().copy(creators = listOf(ImportedPerson("n".repeat(OVER_LONG_NAME), emptyList()))),
                aPin().copy(publisher = ImportedPerson("Ada", List(OVER_LONG_URLS) { "https://$it.example" })),
                withAddress(" "),
                withAddress("https://a.example/" + "x".repeat(OVER_LONG_URL)),
                aPin().copy(creators = List(OVER_LONG_REFS) { ImportedPerson("p$it", emptyList()) }),
            )
        val source =
            FakeArchiveSource(
                manifest = aManifest(),
                pins = lines.mapIndexed { index, line -> TestLine(index + 1, line) },
                media = everyMedium,
            )
        stubWalk(source)
        stubIssues()

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        assertEquals(List(lines.size) { UserDataImportIssueKind.FIELD_INVALID }, kinds())
        assertTrue(savedPins.isEmpty())
        verify(exactly = 0) { personRepository.savePerson(any()) }
        verify(exactly = 0) { mediaStore.digest(any(), any()) }
    }

    /** A created person is one the lookup answers with from then on, as a repository would. */
    private fun stubPersons(vararg existing: Person) {
        val known = existing.toMutableList()
        every { personRepository.findUserPerson(user, any(), any()) } answers
            {
                known.firstOrNull { person ->
                    person.name == secondArg<String>() && person.urls.toSet() == thirdArg<Collection<String>>().toSet()
                }
            }
        every { personRepository.savePerson(any()) } answers
            {
                firstArg<Person>().also { person ->
                    savedPersons += person
                    known += person
                }
            }
    }

    private companion object {
        const val OVER_LONG_URLS = 21
        const val OVER_LONG_URL = 2000
    }
}
