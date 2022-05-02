package no.nav.saas.proxy.token

import java.net.URL
import mu.KotlinLogging
import no.nav.saas.proxy.toNavRequest
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import org.http4k.core.Request

const val env_AZURE_APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
const val env_AZURE_APP_CLIENT_ID = "AZURE_APP_CLIENT_ID"

const val claim_NAME = "name"

object TokenValidation {

    private val log = KotlinLogging.logger { }

    val validators: MutableMap<String, JwtTokenValidationHandler?> = mutableMapOf()

    private fun addValidator(clientId: String): JwtTokenValidationHandler {
        val validationHandler = JwtTokenValidationHandler(MultiIssuerConfiguration(
            mapOf("azure" to IssuerProperties(
                    URL(System.getenv(env_AZURE_APP_WELL_KNOWN_URL)),
                    listOf(clientId))
            )
        ))
        validators[clientId] = validationHandler
        return validationHandler
    }

    fun validatorFor(clientId: String): JwtTokenValidationHandler {
        return validators.get(clientId) ?: addValidator(clientId)
    }

    fun containsValidToken(request: Request, clientId: String): Boolean {
        val firstValidToken = validatorFor(clientId).getValidatedTokens(request.toNavRequest()).firstValidToken
        if (firstValidToken.isPresent) {
            // log.info { "Contains name claim: ${(firstValidToken.get().jwtTokenClaims.get("name") != null)}" }
        }
        return firstValidToken.isPresent
    }
}
