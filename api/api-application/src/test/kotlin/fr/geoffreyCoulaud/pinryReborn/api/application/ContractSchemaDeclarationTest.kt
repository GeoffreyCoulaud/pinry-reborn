package fr.geoffreyCoulaud.pinryReborn.api.application

import com.fasterxml.jackson.databind.JsonNode
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoAnnotationDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.ext.list.withAnnotationNamed
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.DownloadStatus
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataExportState
import fr.geoffreyCoulaud.pinryReborn.api.domain.enums.UserDataImportState
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.DownloadReasonDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.dtos.output.UserDataImportIssueKindDto
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.BaseErrorMapper
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemCode
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.mappers.ProblemResponses.PROBLEM_JSON_MEDIA_TYPE
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.openapi.SharedRefusalsFilter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.PinImageStatus
import fr.geoffreyCoulaud.pinryReborn.api.usecases.exceptions.ErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The contract declares what the server emits: an opaque cursor, the values of a status filled
 * from an enum's name, the status code a route builds for itself, and the codes a refusal carries.
 */
class ContractSchemaDeclarationTest {
    private val nullableString = setOf("string", "null")
    private val regenerate = "./gradlew :api-application:quarkusAppPartsBuild --rerun"

    private val operationsCarryingACursor =
        listOf(
            "GET /api/v1/pins",
            "GET /api/v1/pins/recycled",
            "GET /api/v1/boards/{boardId}/pins",
            "GET /api/v1/me/exports",
            "GET /api/v1/me/imports",
            "GET /api/v1/me/imports/{id}/issues",
        )

    @Test
    fun `Given the published contract, Then every cursor query parameter is declared a nullable string`() {
        // Given
        val declared = cursorParameterTypes()

        // When
        val wrong = declared.filterValues { it != nullableString }

        // Then
        assertTrue(
            declared.keys.containsAll(operationsCarryingACursor),
            "Expected a cursor query parameter on $operationsCarryingACursor, found one on ${declared.keys}.",
        )
        assertEquals(
            emptyMap<String, Set<String>>(),
            wrong,
            "These operations declare a cursor the wire never carries. Regenerate after fixing the " +
                "schema: $regenerate",
        )
    }

    @Test
    fun `Given the published contract, Then both pagination cursors are declared nullable strings`() {
        // Given
        val properties = PublishedContract.schema("PaginationOutputDto").path("properties")

        // When
        val wrong =
            listOf("previousCursor", "nextCursor")
                .associateWith { effectiveTypes(properties.path(it)) }
                .filterValues { it != nullableString }

        // Then
        assertEquals(
            emptyMap<String, Set<String>>(),
            wrong,
            "PaginationOutputDto serialises both cursors through Base64JsonSerializer, so both are " +
                "strings on the wire. Regenerate after fixing the schema: $regenerate",
        )
    }

    @Test
    fun `Given the published contract, Then the pin image status declares the values the server emits`() {
        // Given
        val status = PublishedContract.schema("PinImageStateDto").path("properties").path("status")

        // Then
        assertEquals(
            PinImageStatus.entries.map { it.name }.toSet(),
            enumeration(status),
            "PinImageStateMapper fills this field from PinImageStatus, and it is the discriminator " +
                "every tile of the grid reads. Regenerate after fixing the schema: $regenerate",
        )
    }

    @Test
    fun `Given the published contract, Then the replacement status declares the values the server emits`() {
        // Given
        val status = PublishedContract.schema("ReplacementDto").path("properties").path("status")

        // Then
        assertEquals(
            DownloadStatus.entries.map { it.name }.toSet(),
            enumeration(status),
            "PinImageStateMapper fills this field from DownloadStatus, the same field one level " +
                "down. Regenerate after fixing the schema: $regenerate",
        )
    }

    @Test
    fun `Given the published contract, Then both data states declare the values the server emits`() {
        // Given
        val exportState = PublishedContract.schema("UserDataExportOutputDto").path("properties").path("state")
        val importState = PublishedContract.schema("UserDataImportOutputDto").path("properties").path("state")

        // Then
        val why = "The mappers fill it from the domain's state, which a client's behaviour hangs on. Regenerate: "
        assertEquals(UserDataExportState.entries.map { it.name }.toSet(), enumeration(exportState), why + regenerate)
        assertEquals(UserDataImportState.entries.map { it.name }.toSet(), enumeration(importState), why + regenerate)
    }

    @Test
    fun `Given the published contract, Then every open code lists its values as x-extensible-enum and no enum`() {
        // Given
        val twins = mapOf(
            "DownloadReasonDto" to DownloadReasonDto.entries.map { it.name },
            "UserDataImportIssueKindDto" to UserDataImportIssueKindDto.entries.map { it.name },
        )

        // When
        val wrong = twins.filter { (name, values) ->
            val component = PublishedContract.schema(name)
            component.path("x-extensible-enum").map { it.asText() } != values || component.has("enum")
        }.keys

        // Then
        assertEquals(
            emptySet<String>(),
            wrong,
            "A closed enum makes every new value a contract major, and ExtensibleEnumsFilter opens these " +
                "(docs/adr/0044-a-response-code-declares-its-set.md). Regenerate: $regenerate",
        )
    }

    @Test
    fun `Given the published contract, Then every position of an open code references its component`() {
        // Given
        val positions = mapOf(
            "PinImageStateDto.reasonCode" to "DownloadReasonDto",
            "ReplacementDto.reasonCode" to "DownloadReasonDto",
            "ImageDownloadOutputDto.reasonCode" to "DownloadReasonDto",
            "UserDataImportIssueOutputDto.kind" to "UserDataImportIssueKindDto",
        )

        // When
        val wrong = positions.filter { (position, component) ->
            val property = PublishedContract.schema(position.substringBefore('.'))
                .path("properties").path(position.substringAfter('.'))
            component !in (listOf(property) + property.path("anyOf")).mapNotNull { it.path("\$ref").textValue() }
                .map { it.substringAfterLast('/') }
        }.keys

        // Then
        assertEquals(emptySet<String>(), wrong, "These fields no longer carry their twin. Regenerate: $regenerate")
    }

    @Test
    fun `Given the published contract, Then every route declares the success codes it builds`() {
        // Given
        val built = successCodesBuiltPerRoute()

        // When
        val wrong = built.filterKeys { declaredSuccessCodes(it) != built.getValue(it) }

        // Then
        assertEquals(
            emptyMap<String, Set<String>>(),
            wrong.mapValues { (route, codes) -> "builds $codes, declares ${declaredSuccessCodes(route)}" },
            "SmallRye reads the status off the return type, so a route that builds another one " +
                "declares it with @APIResponse as BoardController does. Regenerate after " +
                "annotating: $regenerate",
        )
    }

    @Test
    fun `Given the published contract, Then every declared refusal code is a ProblemCode`() {
        // Given
        val declared = declaredRefusalCodes()
        val known = ProblemCode.entries.map { it.name }.toSet()

        // When
        val unknown = declared.mapValues { (_, codes) -> codes - known }.filterValues { it.isNotEmpty() }

        // Then
        assertTrue(declared.values.any { it.isNotEmpty() }, "The contract declares no refusal code at all.")
        assertEquals(
            emptyMap<String, Set<String>>(),
            unknown,
            "An annotation's enumeration is a string the compiler does not check; these name no " +
                "ProblemCode. Regenerate after fixing the annotation: $regenerate",
        )
    }

    @Test
    fun `Given the published contract, Then every declared refusal carries its codes`() {
        // Given
        val declared = declaredRefusalCodes()

        // When
        val codeless = declared.filterValues { it.isEmpty() }.keys

        // Then
        assertEquals(
            emptySet<String>(),
            codeless,
            "A refusal the contract declares carries a ProblemDetail and the enum of its codes " +
                "(docs/adr/0042-the-presentation-owns-the-refusal-codes.md). Regenerate after " +
                "declaring them: $regenerate",
        )
    }

    @Test
    fun `Given the published contract, Then every declared refusal code sits under the status it is answered with`() {
        // Given
        val mapper = BaseErrorMapper()
        val statusOf = FRAMEWORK_STATUSES +
            ErrorCode.entries.map { mapper.problemFor(it) }.associate { (code, status) -> code.name to "$status" }

        // When
        val misplaced = declaredRefusalCodes().flatMap { (refusal, codes) ->
            codes.filter { statusOf[it] != refusal.substringAfterLast(' ') }.map { "$refusal $it: ${statusOf[it]}" }
        }

        // Then
        assertEquals(
            emptyList<String>(),
            misplaced,
            "These codes are declared under a status the server never answers them with, the one after the " +
                "colon. A framework code with none takes its entry in FRAMEWORK_STATUSES. Regenerate: $regenerate",
        )
    }

    @Test
    fun `Given the published contract, Then every operation reading a body declares BODY_TOO_LARGE`() {
        // Given
        val readingABody = operations().filterValues { it.has("requestBody") }

        // When
        val missing = readingABody.filterValues { operation ->
            val tooLarge = operation.path("responses").path("413")
            tooLarge.isMissingNode || ProblemCode.BODY_TOO_LARGE.name !in refusalCodes(tooLarge)
        }.keys

        // Then
        assertTrue(readingABody.isNotEmpty(), "The contract declares no operation reading a body.")
        assertEquals(
            emptySet<String>(),
            missing,
            "OversizeBodyRefusal answers every route, and SharedRefusalsFilter declares it on every operation " +
                "reading a body. Regenerate: $regenerate",
        )
    }

    @Test
    fun `Given the published contract, Then every protected operation's 401 is the shared one`() {
        // Given
        val shared = "#/components/responses/${SharedRefusalsFilter.UNAUTHENTICATED}"

        // When
        val wrong = operations()
            .filterValues { !it.path("security").isEmpty }
            .filterValues { it.path("responses").path("401").path("\$ref").asText() != shared }
            .keys

        // Then
        assertEquals(
            emptySet<String>(),
            wrong,
            "SharedRefusalsFilter points every protected operation's 401 at $shared. Regenerate: $regenerate",
        )
    }

    @Test
    fun `Given production sources, Then every route building a response of its own is read here`() {
        // Given
        val routes = restResponseRoutes()

        // When
        val unread = routes.filter { statusesIn(it).isEmpty() }.map { it.name }

        // Then
        assertTrue(routes.isNotEmpty(), "Konsist found no controller endpoint at all, so nothing here is read.")
        assertEquals(
            emptyList<String>(),
            unread,
            "These routes return a RestResponse and build no status this test recognises, so the " +
                "assertion above reads nothing of them. Add the builder to STATUS_BUILDERS.",
        )
    }

    /**
     * The success codes each route builds, keyed as the contract's operation. SmallRye merges the
     * two `@Consumes`-differentiated functions of `PUT /{pinId}/image`, so codes union per route.
     */
    private fun successCodesBuiltPerRoute(): Map<String, Set<String>> =
        endpoints()
            .groupBy { it.name }
            .mapValues { (_, functions) -> functions.flatMap { statusesIn(it) }.toSet() }
            .filterValues { it.isNotEmpty() }

    /** The 2xx codes the contract declares for an operation, empty when it declares no such route. */
    private fun declaredSuccessCodes(route: String): Set<String> =
        PublishedContract.document
            .path("paths")
            .path(route.substringAfter(' '))
            .path(route.substringBefore(' ').lowercase())
            .path("responses")
            .properties()
            .map { (code, _) -> code }
            .filter { it.startsWith("2") }
            .toSet()

    /**
     * The statuses a route builds, read from its own text and from the private functions it calls
     * by name: `downloadExport` and `getImage` both hand the building to one.
     */
    private fun statusesIn(endpoint: Endpoint): Set<String> {
        val delegated = endpoint.helpers.filter { endpoint.function.text.contains("${it.name}(") }
        val text = endpoint.function.text + delegated.joinToString("\n") { it.text }
        return STATUS_BUILDERS.filterKeys { text.contains(it) }.values.toSet()
    }

    private fun restResponseRoutes(): List<Endpoint> =
        endpoints().filter { it.function.returnType?.name?.startsWith("RestResponse") == true }

    /** Every endpoint of every controller, named as the contract names its operation. */
    private fun endpoints(): List<Endpoint> =
        Konsist
            .scopeFromProduction(moduleName = "api-presentation-quarkus")
            .classes()
            .withAnnotationNamed(PATH)
            .flatMap { controller ->
                val base = controller.annotations.first { it.name == PATH }.pathValue()
                val helpers = controller.functions().filter { it.hasPrivateModifier }
                controller.functions().mapNotNull { function ->
                    val method = function.annotations.firstOrNull { it.name in HTTP_METHODS }
                    val suffix = function.annotations.firstOrNull { it.name == PATH }?.pathValue().orEmpty()
                    method?.let { Endpoint("${it.name} $base$suffix", function, helpers) }
                }
            }

    private fun KoAnnotationDeclaration.pathValue(): String =
        arguments.first().value?.trim('"').orEmpty()

    private class Endpoint(
        val name: String,
        val function: KoFunctionDeclaration,
        val helpers: List<KoFunctionDeclaration>,
    )

    private companion object {
        const val PATH = "Path"
        val HTTP_METHODS = setOf("GET", "POST", "PUT", "DELETE", "PATCH")

        /** The status of each code the framework mappers and the HTTP layer answer, `problemFor` owning the rest. */
        val FRAMEWORK_STATUSES = mapOf(
            ProblemCode.VALIDATION_ERROR.name to "400",
            ProblemCode.MALFORMED_BODY.name to "400",
            ProblemCode.AUTHENTICATION_REQUIRED.name to "401",
            ProblemCode.AUTHENTICATION_FAILED.name to "401",
            ProblemCode.SESSION_EXPIRED.name to "401",
            ProblemCode.UNKNOWN_ROUTE.name to "404",
            ProblemCode.BODY_TOO_LARGE.name to "413",
            ProblemCode.UNSUPPORTED_MEDIA_TYPE.name to "415",
            ProblemCode.RANGE_NOT_SATISFIABLE.name to "416",
        )

        /**
         * What a route writes to name a status, and the status it names. `notModified` is left out:
         * `304` is not a success, and SmallRye declares none of the routes that build it.
         */
        val STATUS_BUILDERS = mapOf(
            ".created<" to "201",
            "Status.CREATED" to "201",
            "Status.ACCEPTED" to "202",
            "Status.NO_CONTENT" to "204",
            "noContent()" to "204",
            "Status.PARTIAL_CONTENT" to "206",
            "Status.OK" to "200",
            ".ok(" to "200",
        )
    }

    /** Every operation of the contract, keyed as `METHOD /path`. */
    private fun operations(): Map<String, JsonNode> =
        PublishedContract.document
            .path("paths")
            .properties()
            .flatMap { (path, operations) ->
                operations.properties().map { (method, operation) -> "${method.uppercase()} $path" to operation }
            }.toMap()

    /** The codes each refusal declares, keyed as `METHOD /path status`, a shared entry read through its `$ref`. */
    private fun declaredRefusalCodes(): Map<String, Set<String>> =
        operations().flatMap { (name, operation) ->
            operation.path("responses").properties()
                .filterNot { (status, _) -> status.startsWith("2") }
                .map { (status, response) -> "$name $status" to refusalCodes(response) }
        }.toMap()

    private fun refusalCodes(response: JsonNode): Set<String> {
        val resolved = if (response.has("\$ref")) {
            PublishedContract.document.at(response.path("\$ref").asText().removePrefix("#"))
        } else {
            response
        }
        assertTrue(!resolved.isMissingNode, "The contract names ${response.path("\$ref")}, which it declares nowhere.")
        return enumeration(
            resolved.path("content").path(PROBLEM_JSON_MEDIA_TYPE).path("schema").path("properties").path("code"),
        )
    }

    /** Every `cursor` query parameter the contract declares, keyed by the operation carrying it. */
    private fun cursorParameterTypes(): Map<String, Set<String>> =
        PublishedContract.document
            .path("paths")
            .properties()
            .flatMap { (path, operations) ->
                operations.properties().flatMap { (method, operation) ->
                    operation
                        .path("parameters")
                        .filter { it.path("name").asText() == "cursor" }
                        .map { "${method.uppercase()} $path" to effectiveTypes(it.path("schema")) }
                }
            }.toMap()

    private fun effectiveTypes(schema: JsonNode): Set<String> =
        resolve(schema) { node ->
            node.path("type").let { type ->
                if (type.isArray) type.map { it.asText() }.toSet() else setOf(type.asText())
            }
        }

    private fun enumeration(schema: JsonNode): Set<String> =
        resolve(schema) { node -> node.path("enum").map { it.asText() }.toSet() }

    /** Follows `$ref` and unions over `anyOf`, which is how SmallRye spells a nullable reference. */
    private fun resolve(schema: JsonNode, read: (JsonNode) -> Set<String>): Set<String> =
        when {
            schema.has("\$ref") ->
                resolve(PublishedContract.schema(schema.path("\$ref").asText().substringAfterLast('/')), read)
            schema.has("anyOf") -> schema.path("anyOf").flatMap { resolve(it, read) }.toSet()
            else -> read(schema)
        }
}
