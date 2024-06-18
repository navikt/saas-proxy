package no.nav.saas.proxy.token

import mu.KotlinLogging
import no.nav.saas.proxy.env
import no.nav.saas.proxy.env_AZURE_APP_CLIENT_ID
import no.nav.saas.proxy.env_AZURE_APP_WELL_KNOWN_URL
import no.nav.saas.proxy.env_WHITELIST_FILE
import no.nav.saas.proxy.toNavRequest
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.jwt.JwtToken
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import org.http4k.core.Request
import java.net.URL
import java.util.Optional

const val claim_NAME = "name"

object TokenValidation {

    private val log = KotlinLogging.logger { }

    val validators: MutableMap<String, JwtTokenValidationHandler?> = mutableMapOf()

    private fun addValidator(clientId: String): JwtTokenValidationHandler {
        val validationHandler = JwtTokenValidationHandler(
            MultiIssuerConfiguration(
                mapOf(
                    "azure" to IssuerProperties(
                        URL(System.getenv(env_AZURE_APP_WELL_KNOWN_URL)),
                        listOf(clientId)
                    )
                )
            )
        )
        validators[clientId] = validationHandler
        return validationHandler
    }

    fun validatorFor(clientId: String): JwtTokenValidationHandler {
        return validators.get(clientId) ?: addValidator(clientId)
    }

    fun firstValidToken(request: Request): Optional<JwtToken> =
        validatorFor(env(env_AZURE_APP_CLIENT_ID)).getValidatedTokens(request.toNavRequest()).firstValidToken

    fun containsValidToken(request: Request, clientId: String): Boolean {
        if (isDev && clientId == "skip") return true
        val firstValidToken: Optional<JwtToken> = validatorFor(clientId).getValidatedTokens(request.toNavRequest()).firstValidToken
        // For separation of OBO token and machine token:
        // if (firstValidToken.isPresent) {
        // log.info { "Contains name claim: ${(firstValidToken.get().jwtTokenClaims.get("name") != null)}" }
        // }
        return firstValidToken.isPresent
    }

    val isDev = (System.getenv(env_WHITELIST_FILE) == "/whitelist/dev.json")
}
