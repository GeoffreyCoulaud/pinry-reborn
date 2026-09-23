package fr.geoffreyCoulaud.pinryReborn.api.application

import fr.geoffreyCoulaud.pinryReborn.api.persistence.sqlite.models.SessionTokenModel
import fr.geoffreyCoulaud.pinryReborn.api.utilities.createRandomString
import io.ebean.DB
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasKey
import org.hamcrest.Matchers.matchesPattern
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

@QuarkusTest
// The app under test runs the real SystemClock; these read the wall clock to keep fixture instants consistent with it.
@Suppress("WallClockRead")
class SessionAuthIntegrationTest : IntegrationTest() {
    private val iso8601Utc = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z"

    private fun login(
        name: String,
        password: String = DEFAULT_PASSWORD,
        rememberMe: Boolean? = null,
        transport: String = "BEARER",
    ) =
        given().contentType(ContentType.JSON)
            .body(buildMap<String, Any> {
                put("name", name); put("password", password); put("transport", transport)
                if (rememberMe != null) put("rememberMe", rememberMe)
            })
            .post("/api/v1/sessions")

    /** A user with a live cookie session, returning the cookie's value. */
    private fun cookieSession(rememberMe: Boolean? = null): String {
        val name = createRandomString()
        userCreator.createUserWithPassword(name, DEFAULT_PASSWORD)
        return login(name, rememberMe = rememberMe, transport = "COOKIE")
            .then().statusCode(200).extract().cookie(SESSION_COOKIE)
    }

    @Test
    fun `Given valid credentials, Then POST sessions returns 201 with a token and ISO-8601 UTC metadata`() {
        val name = createRandomString()
        userCreator.createUserWithPassword(name, DEFAULT_PASSWORD)
        login(name)
            .then().statusCode(201)
            .header("Cache-Control", "no-store")
            .body("token", notNullValue())
            .body("expiresAt", matchesPattern(iso8601Utc))
            .body("renewAfter", matchesPattern(iso8601Utc))
    }

    @Test
    fun `Given the bearer transport, Then POST sessions sets no cookie`() {
        val name = createRandomString()
        userCreator.createUserWithPassword(name, DEFAULT_PASSWORD)
        login(name).then().statusCode(201).header("Set-Cookie", nullValue())
    }

    @Test
    fun `Given the cookie transport, Then POST sessions answers 200 with a hardened cookie and no token`() {
        // Given
        val name = createRandomString()
        userCreator.createUserWithPassword(name, DEFAULT_PASSWORD)

        // When
        val response = login(name, rememberMe = true, transport = "COOKIE")
            .then().statusCode(200)
            .body("$", not(hasKey("token")))
            .body("persistent", equalTo(true))
            .extract()

        // Then
        val cookie = response.detailedCookie(SESSION_COOKIE)
        assertTrue(cookie.isHttpOnly, "A script that can read the token defeats the transport")
        assertTrue(cookie.isSecured)
        assertEquals("Strict", cookie.sameSite, "SameSite=Strict is what closes CSRF")
        assertNotNull(cookie.expiryDate, "rememberMe maps onto the cookie's lifetime")
    }

    @Test
    fun `Given a cookie session, Then the next request authenticates on the cookie with no header`() {
        // Given / When / Then: the claim the whole lot rests on, exercised rather than read
        given().cookie(SESSION_COOKIE, cookieSession()).get("/api/v1/me").then().statusCode(200)
    }

    @Test
    fun `Given a cookie and a header for two sessions, Then the header's session is the one that authenticates`() {
        // Given
        val bearer = createAuthenticatedUser()
        val cookie = cookieSession()

        // When / Then
        given().authenticatedAs(bearer).cookie(SESSION_COOKIE, cookie)
            .get("/api/v1/me")
            .then().statusCode(200).body("name", equalTo(bearer.user.name))
    }

    @Test
    fun `Given a cookie session, Then renew answers 200 with a fresh cookie and no token`() {
        // Given
        val cookie = cookieSession()

        // When
        val renewed = given().cookie(SESSION_COOKIE, cookie).post("/api/v1/sessions/current/renew")
            .then().statusCode(200).body("$", not(hasKey("token")))
            .extract().cookie(SESSION_COOKIE)

        // Then
        given().cookie(SESSION_COOKIE, cookie).get("/api/v1/me").then().statusCode(401)
        given().cookie(SESSION_COOKIE, renewed).get("/api/v1/me").then().statusCode(200)
    }

    @Test
    fun `Given a cookie session, Then DELETE sessions current clears the cookie and the session`() {
        // Given
        val cookie = cookieSession()

        // When
        val cleared = given().cookie(SESSION_COOKIE, cookie).delete("/api/v1/sessions/current")
            .then().statusCode(204).extract().detailedCookie(SESSION_COOKIE)

        // Then
        assertEquals(0, cleared.maxAge)
        given().cookie(SESSION_COOKIE, cookie).get("/api/v1/me").then().statusCode(401)
    }

    @Test
    fun `Given a bad password, Then POST sessions returns 401 AUTHENTICATION_FAILED`() {
        val name = createRandomString()
        userCreator.createUserWithPassword(name, DEFAULT_PASSWORD)
        login(name, password = "wrong-password")
            .then().statusCode(401).body("code", org.hamcrest.Matchers.equalTo("AUTHENTICATION_FAILED"))
    }

    @Test
    fun `Given an unknown user, Then POST sessions returns 401 AUTHENTICATION_FAILED`() {
        login(createRandomString())
            .then().statusCode(401).body("code", org.hamcrest.Matchers.equalTo("AUTHENTICATION_FAILED"))
    }

    @Test
    fun `Given a garbage token on a public route, Then the route answers as it would without one`() {
        // A revoked cookie stays in the browser, and signing in again must still work.
        // Given
        val name = createRandomString()
        userCreator.createUserWithPassword(name, DEFAULT_PASSWORD)

        // When
        val response = given().header("Authorization", "Bearer not-a-real-token").contentType(ContentType.JSON)
            .body(mapOf("name" to name, "password" to DEFAULT_PASSWORD, "transport" to "BEARER"))
            .post("/api/v1/sessions")

        // Then
        response.then().statusCode(201)
    }

    @Test
    fun `Given a valid token, Then GET me returns the caller`() {
        val auth = createAuthenticatedUser()
        given().authenticatedAs(auth).get("/api/v1/me")
            .then().statusCode(200).body("name", org.hamcrest.Matchers.equalTo(auth.user.name))
    }

    @Test
    fun `Given no token, Then GET me returns 401 with a Bearer challenge`() {
        given().get("/api/v1/me")
            .then().statusCode(401).header("WWW-Authenticate", "Bearer")
    }

    @Test
    fun `Given a garbage token, Then GET me returns 401 AUTHENTICATION_FAILED`() {
        given().header("Authorization", "Bearer not-a-real-token").get("/api/v1/me")
            .then().statusCode(401).body("code", org.hamcrest.Matchers.equalTo("AUTHENTICATION_FAILED"))
    }

    @Test
    fun `Given a token, Then GET sessions current returns expiry metadata without a token`() {
        val auth = createAuthenticatedUser(rememberMe = true)
        given().authenticatedAs(auth).get("/api/v1/sessions/current")
            .then().statusCode(200)
            .body("expiresAt", matchesPattern(iso8601Utc))
            .body("renewAfter", matchesPattern(iso8601Utc))
            .body("persistent", org.hamcrest.Matchers.equalTo(true))
            .body("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("token")))
    }

    @Test
    fun `Given a token, Then renew returns a new token and the old one is rejected`() {
        val auth = createAuthenticatedUser()
        val newToken = given().authenticatedAs(auth).post("/api/v1/sessions/current/renew")
            .then().statusCode(201).header("Cache-Control", "no-store").extract().path<String>("token")
        assertNotNull(newToken)
        // Old token now rejected:
        given().authenticatedAs(auth).get("/api/v1/me").then().statusCode(401)
        // New token works:
        given().header("Authorization", "Bearer $newToken").get("/api/v1/me").then().statusCode(200)
    }

    @Test
    fun `Given a token, Then DELETE sessions current logs it out`() {
        val auth = createAuthenticatedUser()
        given().authenticatedAs(auth).delete("/api/v1/sessions/current").then().statusCode(204)
        given().authenticatedAs(auth).get("/api/v1/me").then().statusCode(401)
    }

    @Test
    fun `Given two sessions for one user, Then DELETE sessions revokes them all`() {
        val name = createRandomString()
        userCreator.createUserWithPassword(name, DEFAULT_PASSWORD)
        val first = login(name).then().statusCode(201).extract().path<String>("token")
        val second = login(name).then().statusCode(201).extract().path<String>("token")

        given().header("Authorization", "Bearer $first").delete("/api/v1/sessions").then().statusCode(204)

        given().header("Authorization", "Bearer $first").get("/api/v1/me")
            .then().statusCode(401).body("code", org.hamcrest.Matchers.equalTo("AUTHENTICATION_FAILED"))
        given().header("Authorization", "Bearer $second").get("/api/v1/me")
            .then().statusCode(401).body("code", org.hamcrest.Matchers.equalTo("AUTHENTICATION_FAILED"))
    }

    @Test
    fun `Given an expired token, Then it is rejected with 401 SESSION_EXPIRED`() {
        // Spec §12: a dedicated SessionExpiredException subtype was tried in Task 9 but never routed
        // through the JAX-RS exception-mapper chain at runtime. AuthenticationFailedExceptionMapper now
        // inspects the exception's cause instead, distinguishing an expired token from an invalid one
        // while reusing the (proven to route) AuthenticationFailedException path.
        val auth = createAuthenticatedUser()
        // Age the single session-token row directly via Ebean (deterministic, no clock mocking).
        val model = DB.getDefault().find(SessionTokenModel::class.java).findList().single()
        model.expiresAt = Instant.now().minusSeconds(60)
        DB.getDefault().save(model)

        given().authenticatedAs(auth).get("/api/v1/me")
            .then().statusCode(401).body("code", org.hamcrest.Matchers.equalTo("SESSION_EXPIRED"))
    }

    // What the framework refuses before a use case runs shares the problem format: one case per row of
    // docs/specs/2026-09-05-p2-debt-elimination.md section 4.5.

    @Test
    fun `Given a body that is not JSON, Then POST sessions returns 400 MALFORMED_BODY`() {
        given().contentType(ContentType.JSON).body("{not json").post("/api/v1/sessions")
            .then().statusCode(400).contentType(PROBLEM_JSON).body("code", equalTo("MALFORMED_BODY"))
    }

    @Test
    fun `Given a path no resource serves, Then the response is 404 UNKNOWN_ROUTE`() {
        given().get("/api/v1/nowhere")
            .then().statusCode(404).contentType(PROBLEM_JSON).body("code", equalTo("UNKNOWN_ROUTE"))
    }

    @Test
    fun `Given a path value the framework cannot read, Then the response is 404 UNKNOWN_ROUTE saying so`() {
        // Given: JAX-RS 3.2 makes a path parameter it cannot convert a NotFoundException carrying the cause
        val auth = createAuthenticatedUser()
        given().authenticatedAs(auth).get("/api/v1/pins/not-a-uuid")
            .then().statusCode(404).contentType(PROBLEM_JSON).body("code", equalTo("UNKNOWN_ROUTE"))
            .body("detail", equalTo("A path or query value could not be read"))
    }

    @Test
    fun `Given a method the path does not serve, Then the response is 405 METHOD_NOT_ALLOWED`() {
        // PUT, the collection now serving GET, POST and DELETE alike.
        given().put("/api/v1/pins")
            .then().statusCode(405).contentType(PROBLEM_JSON).body("code", equalTo("METHOD_NOT_ALLOWED"))
    }

    @Test
    fun `Given a content type the route does not read, Then the response is 415 UNSUPPORTED_MEDIA_TYPE`() {
        given().contentType(ContentType.TEXT).body("name=x").post("/api/v1/sessions")
            .then().statusCode(415).contentType(PROBLEM_JSON).body("code", equalTo("UNSUPPORTED_MEDIA_TYPE"))
    }

    @Test
    fun `Given an Accept the route cannot produce, Then the response is 406 NOT_ACCEPTABLE`() {
        given().accept(ContentType.TEXT).get("/test/failures/json-only")
            .then().statusCode(406).contentType(PROBLEM_JSON).body("code", equalTo("NOT_ACCEPTABLE"))
    }

    @Test
    fun `Given a WebApplicationException no mapper names, Then the response keeps its status under HTTP_ERROR`() {
        given().get("/test/failures/service-unavailable")
            .then().statusCode(503).contentType(PROBLEM_JSON).body("code", equalTo("HTTP_ERROR"))
    }

    @Test
    fun `Given an unmapped exception, Then the response is 500 INTERNAL_ERROR and tells nothing of it`() {
        given().get("/test/failures/illegal-state")
            .then().statusCode(500).contentType(PROBLEM_JSON)
            .body("code", equalTo("INTERNAL_ERROR")).body("detail", nullValue())
            .body(not(containsString(TestFailuresResource.MARKER)))
    }

    @Test
    fun `Given an IOException, Then the response is 500 INTERNAL_ERROR and tells nothing of it`() {
        given().get("/test/failures/io")
            .then().statusCode(500).contentType(PROBLEM_JSON)
            .body("code", equalTo("INTERNAL_ERROR")).body("detail", nullValue())
            .body(not(containsString(TestFailuresResource.MARKER)))
    }

    private companion object {
        const val PROBLEM_JSON = "application/problem+json"
        const val SESSION_COOKIE = "pinry_session"
    }
}
