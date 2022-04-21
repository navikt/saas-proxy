package no.nav.saas.proxy.token

import java.net.URL
import no.nav.security.token.support.core.configuration.IssuerProperties
import no.nav.security.token.support.core.configuration.MultiIssuerConfiguration
import no.nav.security.token.support.core.http.HttpRequest
import no.nav.security.token.support.core.validation.JwtTokenValidationHandler
import org.http4k.core.Request

const val env_AZURE_APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
const val env_AZURE_APP_CLIENT_ID = "AZURE_APP_CLIENT_ID"

const val claim_NAME = "name"

object TokenValidation {
    val multiIssuerConfiguration = MultiIssuerConfiguration(
        mapOf(
            "azure" to IssuerProperties(
                URL(System.getenv("AZURE_APP_WELL_KNOWN_URL")),
                listOf(System.getenv("AZURE_APP_CLIENT_ID"))
            )
        )
    )

    private val jwtTokenValidationHandler = JwtTokenValidationHandler(multiIssuerConfiguration)

    fun containsValidToken(request: Request): Boolean {
        val firstValidToken = jwtTokenValidationHandler.getValidatedTokens(fromHttp4kRequest(request)).firstValidToken
        return firstValidToken.isPresent
    }

    private fun fromHttp4kRequest(
        request: Request
    ): HttpRequest {
        return object : HttpRequest {
            override fun getHeader(headerName: String): String {
                return request.header(headerName) ?: ""
            }

            override fun getCookies(): Array<HttpRequest.NameValue> {
                return arrayOf()
            }
        }
    }
}
