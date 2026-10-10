package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.Pin
import fr.geoffreyCoulaud.pinryReborn.api.domain.entities.User
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QMediaModel
import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.query.QTaskModel
import fr.geoffreyCoulaud.pinryReborn.api.presentation.quarkus.config.LoggingRequestResponseFilter
import fr.geoffreyCoulaud.pinryReborn.api.usecases.UserCreator
import fr.geoffreyCoulaud.pinryReborn.api.usecases.tasks.MediaFingerprintTask
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.ebean.DB
import io.ebean.Database
import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.response.ValidatableResponse
import io.restassured.specification.RequestSpecification
import jakarta.inject.Inject
import java.util.UUID
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.junit.jupiter.api.BeforeEach

@Suppress("AbstractClassCanBeConcreteClass") // Abstract by intent: a shared test base for concrete subclasses.
abstract class IntegrationTest {
    @Inject lateinit var userCreator: UserCreator

    private val database: Database
        get() = DB.getDefault()

    /** A created user together with a live bearer token for it. */
    data class AuthenticatedUser(val user: User, val token: String)

    /**
     * Truncate all non-internal tables in the database.
     *
     * - Tables prefixed by "sqlite_" are ignored.
     * - The "db_migration" table is ignored, as it's necessary for ebean.
     *
     * The database is not the only state a case shares: `AuthenticationAttemptLimiter` counts failed password attempts
     * in memory and nothing empties those, so a case submitting a wrong credential takes an identity of its own.
     */
    @BeforeEach
    fun truncateAllTables() {
        database
            .sqlQuery("SELECT name FROM sqlite_master WHERE type='table'")
            .findList()
            .map { it.getString("name") }
            .filterNot { it.startsWith("sqlite_") or it.equals("db_migration") }
            .forEach { database.truncate(it) }
    }

    /** Create a user and log it in, returning the user and a bearer token. */
    protected fun createAuthenticatedUser(
        name: String = createRandomString(),
        password: String = DEFAULT_PASSWORD,
        rememberMe: Boolean = false,
    ): AuthenticatedUser {
        val user = userCreator.createUserWithPassword(name = name, password = password)
        val token =
            RestAssured.given()
                .contentType(ContentType.JSON)
                .body(
                    mapOf("name" to name, "password" to password, "rememberMe" to rememberMe, "transport" to "BEARER")
                )
                .post("/api/v1/sessions")
                .then()
                .statusCode(HTTP_CREATED)
                .extract()
                .path<String>("token")
        return AuthenticatedUser(user, token)
    }

    /**
     * Write a pin over `PUT /api/v1/pins/{pinId}`, which replaces the whole pin: what the case does not change is sent
     * as the pin carries it, so a tag or a board survives a write about the other.
     */
    protected fun replacePin(
        auth: AuthenticatedUser,
        pin: Pin,
        tags: List<String> = pin.tags.map { it.name },
        boardIds: List<UUID> = pin.boards.map { it.id },
    ): ValidatableResponse =
        RestAssured.given()
            .authenticatedAs(auth)
            .contentType(ContentType.JSON)
            .body(
                mapOf(
                    "description" to pin.description,
                    "sourceContextUrl" to pin.sourceContextUrl?.toString(),
                    "sourceMediaUrl" to pin.sourceMediaUrl?.toString(),
                    "tags" to tags,
                    "boardIds" to boardIds.map { it.toString() },
                    "publisher" to pin.publisher?.let { mapOf("name" to it.name, "urls" to it.urls) },
                    "creators" to pin.creators.map { mapOf("name" to it.name, "urls" to it.urls) },
                    "publishedAt" to pin.publishedAt?.toString(),
                )
            )
            .`when`()
            .put("/api/v1/pins/${pin.id}")
            .then()

    /** Attach `Authorization: Bearer <token>` to a REST-Assured request. */
    protected fun RequestSpecification.authenticatedAs(auth: AuthenticatedUser): RequestSpecification =
        header("Authorization", "Bearer ${auth.token}")

    /**
     * Wait long enough for the next stamped instant to differ from the previous one.
     *
     * Stamped instants are truncated to the millisecond, so two writes inside the same millisecond carry the same value
     * and an "instant moved" assertion cannot tell a fresh stamp from a stale one.
     */
    protected fun waitForTheClockToTick() = Thread.sleep(CLOCK_RESOLUTION_MILLIS)

    /** Waits for the worker to hash every media, which stages files of its own while it runs. */
    protected fun awaitFingerprintDrain() {
        repeat(DRAIN_POLL_ATTEMPTS) {
            val draining = QTaskModel().kind.equalTo(MediaFingerprintTask.KIND).state.isIn("PENDING", "RUNNING")
            if (!draining.exists() && !QMediaModel().fingerprintVersion.isNull.exists()) return
            Thread.sleep(DRAIN_POLL_INTERVAL_MILLIS)
        }
        error("The fingerprint drain never settled")
    }

    /** slf4j binds to the JBoss LogManager, which is the JUL one, so a plain JUL handler sees the line. */
    protected fun capturingLogsOf(loggerName: String, action: () -> Unit): List<LogRecord> {
        val records = mutableListOf<LogRecord>()
        val handler =
            object : Handler() {
                override fun publish(record: LogRecord) {
                    records += record
                }

                override fun flush() = Unit

                override fun close() = Unit
            }
        val logger = Logger.getLogger(loggerName)
        logger.addHandler(handler)
        try {
            action()
        } finally {
            logger.removeHandler(handler)
        }
        return records
    }

    /** Every line the request log writes while [action] runs, joined. */
    protected fun requestLogOf(action: () -> Unit): String =
        capturingLogsOf(LoggingRequestResponseFilter::class.java.name, action).joinToString(separator = "\n") {
            it.message.orEmpty()
        }

    companion object {
        const val DEFAULT_PASSWORD = "password123"
        private const val HTTP_CREATED = 201

        /** Long enough to cross a millisecond boundary, the resolution stamped instants keep. */
        private const val CLOCK_RESOLUTION_MILLIS = 2L

        private const val DRAIN_POLL_ATTEMPTS = 100
        private const val DRAIN_POLL_INTERVAL_MILLIS = 200L
    }
}
