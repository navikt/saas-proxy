package no.nav.saas.proxy.token

import mu.KotlinLogging
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

object TokenValidation {

    private val log = KotlinLogging.logger { }

    val validators: MutableMap<String, JwtTokenValidationHandler?> = mutableMapOf()

    val clientIdProxy = System.getenv("AZURE_APP_CLIENT_ID")

    private fun addValidator(clientId: String): JwtTokenValidationHandler {
        val validationHandler = JwtTokenValidationHandler(
            MultiIssuerConfiguration(
                mapOf(
                    "azure" to IssuerProperties(
                        URL(System.getenv(env_AZURE_APP_WELL_KNOWN_URL)),
                        listOf(clientId, clientIdProxy)
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

    // fun firstValidToken(request: Request): Optional<JwtToken> =
    //    validatorFor(env(env_AZURE_APP_CLIENT_ID)).getValidatedTokens(request.toNavRequest()).firstValidToken

    fun firstValidToken(request: Request, clientId: String): Optional<JwtToken> =
        validatorFor(clientId).getValidatedTokens(request.toNavRequest()).firstValidToken

    val isDev = (System.getenv(env_WHITELIST_FILE) == "/whitelist/dev.json")
}
