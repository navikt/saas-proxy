package no.nav.saas.proxy.token

import no.nav.saas.proxy.env
import no.nav.saas.proxy.env_AZURE_APP_CLIENT_ID
import no.nav.saas.proxy.env_AZURE_APP_WELL_KNOWN_URL
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import org.http4k.core.Method
import org.http4k.core.Request
import java.net.URL
import java.util.Optional

object TokenValidation {

    var initialCheckPassed = false

    fun isReady(): Boolean {
        fun createDummyRequest(): Request {
            return Request(Method.GET, "/dummy")
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c")
        }
        return if (initialCheckPassed) {
            true
        } else {
            firstValidToken(createDummyRequest())
            // If Connection to Entra is problematic, the JwtTokenValidationHandler will fail to initialize
            // If reached this part, assume ok
            initialCheckPassed = true
            false
        }
    }

    private val jwtTokenValidationHandler = JwtTokenValidationHandler(
        MultiIssuerConfiguration(
            mapOf(
                "azure" to IssuerProperties(
                    URL(env(env_AZURE_APP_WELL_KNOWN_URL)),
                    listOf(env(env_AZURE_APP_CLIENT_ID))
                )
            )
        )
    )

    fun firstValidToken(request: Request): Optional<JwtToken> =
        jwtTokenValidationHandler.getValidatedTokens(request.toNavRequest()).firstValidToken

    private fun Request.toNavRequest(): HttpRequest {
        val req = this
        return object : HttpRequest {
            override fun getHeader(headerName: String): String {
                return req.header(headerName) ?: ""
            }
            override fun getCookies(): Array<HttpRequest.NameValue> {
                return arrayOf()
            }
        }
    }
}
