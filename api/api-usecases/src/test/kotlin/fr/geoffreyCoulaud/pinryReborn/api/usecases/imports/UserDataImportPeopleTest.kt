package fr.geoffreyCoulaud.pinryReborn.api.usecases.imports

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.HttpUrl
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Person
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.PersonName
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportIssueKind
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportState
import io.mockk.verify
import java.time.Instant
import java.util.UUID.randomUUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `persons.jsonl`, and a pin line's people and publication instant; split for `LargeClass`. */
internal class UserDataImportPeopleTest : UserDataImportRunnerFixtures() {
    @Test
    fun `Given a pin line crediting people, Then each is found or created at the import instant, its date unclamped`() {
        // Given: the creator is already the account's, the publisher is not
        val existing =
            Person(
                id = randomUUID(),
                author = user,
                name = checkNotNull(PersonName.parse("Ada")),
                urls = setOfNotNull(HttpUrl.parse("https://a.example")),
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
        personRepository.seed(existing)

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        val created = savedPins.single()
        assertEquals("Studio", created.publisher?.name?.text)
        assertEquals(now, created.publisher?.createdAt)
        assertEquals(listOf(existing), created.creators)
        assertEquals(beforeAccount, created.publishedAt)
        assertEquals(1, personRepository.saved.size)
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
        assertTrue(personRepository.saved.isEmpty())
        verify(exactly = 0) { mediaStore.digest(any(), any()) }
    }

    // --- persons.jsonl ---

    @Test
    fun `Given person lines reusing an id, with a blank or over-long id or malformed, Then only the first imports`() {
        // Given
        val lines =
            listOf(
                TestLine(1, aPersonLine("ada", "Ada")),
                TestLine(2, aPersonLine("ada", "Grace")),
                TestLine(3, aPersonLine("x".repeat(OVER_LONG_ID), "Hedy")),
                TestLine(4, null, failure = "not JSON"),
                TestLine(5, aPersonLine("blank", " ")),
                TestLine(6, aPersonLine(" ", "Hedy")),
            )
        stubWalk(FakeArchiveSource(manifest = aManifest(), persons = lines))
        stubIssues()

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then: each refusal names the line's id
        assertEquals(listOf("Ada"), personRepository.saved.map { it.name.text })
        val invalid = UserDataImportIssueKind.FIELD_INVALID
        assertEquals(listOf(invalid, invalid, UserDataImportIssueKind.LINE_MALFORMED, invalid, invalid), kinds())
        assertEquals(listOf("ada", "x".repeat(ISSUE_TEXT_LIMIT), null, "blank", " "), savedIssues.map { it.subject })
        assertEquals(5, stored.issueCount)
    }

    @Test
    fun `Given a cancellation landing during the collection walk, Then no person line is imported`() {
        // Given: the collection walk's one write is its report of the malformed line
        val source =
            FakeArchiveSource(
                manifest = aManifest(),
                collections = listOf(TestLine(1, null, failure = "not JSON")),
                persons = listOf(TestLine(1, aPersonLine("ada", "Ada"))),
            )
        stubWalk(source)
        stubIssues()
        cancelWhen { savedIssues.isNotEmpty() }

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        assertEquals(UserDataImportState.CANCELLED, stored.state)
        assertTrue(personRepository.saved.isEmpty())
    }

    @Test
    fun `Given a cancellation landing during the person walk, Then the pin walk never starts`() {
        // Given: the person walk's one write is its report of the malformed line
        val source =
            FakeArchiveSource(
                manifest = aManifest(),
                persons = listOf(TestLine(1, null, failure = "not JSON")),
                pins = listOf(TestLine(1, aPin())),
                media = everyMedium,
            )
        stubWalk(source)
        stubIssues()
        cancelWhen { savedIssues.isNotEmpty() }

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        assertEquals(UserDataImportState.CANCELLED, stored.state)
        assertEquals(0, stored.processedPins)
        verify(exactly = 0) { mediaStore.digest(any(), any()) }
    }

    @Test
    fun `Given a person line created before the account, Then its creation is clamped to the account's`() {
        // Given
        val line = aPersonLine("ada", "Ada", createdAt = beforeAccount)
        stubWalk(FakeArchiveSource(manifest = aManifest(), persons = listOf(TestLine(1, line))))

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        assertEquals(accountCreatedAt, personRepository.saved.single().createdAt)
    }

    @Test
    fun `Given 100 001 person lines, Then the last is refused and the one before it imports`() {
        // Given
        val lines = List(MAX_PERSON_LINES + 1) { index -> TestLine(index + 1, aPersonLine("p$index", "Person $index")) }
        stubWalk(FakeArchiveSource(manifest = aManifest(), persons = lines))
        stubIssues()

        // When
        runner.run(importId, isLastAttempt = false, renewLease)

        // Then
        assertEquals(MAX_PERSON_LINES, personRepository.saved.size)
        assertEquals("Person ${MAX_PERSON_LINES - 1}", personRepository.saved.last().name.text)
        assertEquals(MAX_PERSON_LINES + 1, savedIssues.single { it.kind == UserDataImportIssueKind.FIELD_INVALID }.line)
    }

    private fun aPersonLine(id: String, name: String, createdAt: Instant = pastInstant) =
        ImportedPersonLine(id = id, name = name, urls = listOf("https://$id.example"), createdAt = createdAt)

    private companion object {
        const val OVER_LONG_URLS = 21
        const val OVER_LONG_URL = 2000
        const val OVER_LONG_ID = 201
        const val MAX_PERSON_LINES = 100_000
    }
}
