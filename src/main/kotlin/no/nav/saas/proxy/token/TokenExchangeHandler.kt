package no.nav.saas.proxy.token

import mu.KotlinLogging
import no.nav.saas.proxy.Application
import no.nav.saas.proxy.Metrics
import no.nav.saas.proxy.env
import no.nav.saas.proxy.env_AZURE_APP_CLIENT_ID
import no.nav.saas.proxy.env_AZURE_APP_CLIENT_SECRET
import no.nav.saas.proxy.env_AZURE_OPENID_CONFIG_TOKEN_ENDPOINT
import no.nav.security.token.support.core.jwt.JwtToken
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.body.toBody
import org.json.JSONObject
import java.io.File
import java.lang.Exception
import java.time.Instant
import javax.naming.AuthenticationException

object TokenExchangeHandler {
    /**
     * A handler for azure on-behalf-of exchange flow.
     * @see [v2_oauth2_on_behalf_of_flow](https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-on-behalf-of-flow)
     *
     * Exchanges an azure on-behalf-of-token with audience to this app for one with audience to salesforce. Caches the result
     */

    val log = KotlinLogging.logger { }

    private val client = Application.client

    private val clientId: String = env(env_AZURE_APP_CLIENT_ID)
    private val clientSecret: String = env(env_AZURE_APP_CLIENT_SECRET)

    private val azureTokenEndPoint: String = env(env_AZURE_OPENID_CONFIG_TOKEN_ENDPOINT)

    private var serviceToken: MutableMap<String, JwtToken> = mutableMapOf() // alias, token out
    private var OBOcache: MutableMap<String, JwtToken> = mutableMapOf() // token in (alias + user), token out

    fun isOBOToken(jwt: JwtToken) = jwt.jwtTokenClaims.get("NAVident") != null

    // target alias example: cluster.namespace.app
    fun exchange(jwtIn: JwtToken, targetAlias: String, scope: String): JwtToken {
        if (!isOBOToken(jwtIn)) return acquireServiceToken(targetAlias, scope)
        val key = targetAlias + jwtIn.tokenAsString
        OBOcache[key]?.let { cachedToken ->
            if (cachedToken.jwtTokenClaims.expirationTime.toInstant().minusSeconds(10) > Instant.now()) {
                log.info { "Cached exchange obo token $targetAlias" }
                return cachedToken
            }
        }
        log.info { "Exchange obo token $targetAlias" }
        Metrics.oboCacheSize.set(OBOcache.size.toDouble())

        val req = Request(Method.POST, azureTokenEndPoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(
                listOf(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
                    "assertion" to jwtIn.tokenAsString,
                    "client_id" to clientId,
                    "scope" to "api://$targetAlias/$scope",
                    "client_secret" to clientSecret,
                    "requested_token_use" to "on_behalf_of",
                    "claims" to """{
                        "access_token": {
                            "groups": {
                                "essential": true
                            }
                         }
                    }"""
                ).toBody()
            )
        val res = client(req)

        File("/tmp/exchangerequest").writeText(req.toMessage())
        File("/tmp/exchangeresponse").writeText(res.toMessage())

        val jwt = res.extractAccessToken(targetAlias)
        OBOcache[key] = jwt
        return jwt
    }

    fun acquireServiceToken(targetAlias: String, scope: String): JwtToken {
        serviceToken[targetAlias]?.let { cachedToken ->
            if (cachedToken.jwtTokenClaims.expirationTime.toInstant().minusSeconds(10) > Instant.now()) {
                log.info { "Cached service obo token $targetAlias" }
                return cachedToken
            }
        }
        log.info { "Acquire service token $targetAlias" }
        val req = Request(Method.POST, azureTokenEndPoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(
                listOf(
                    "client_id" to clientId,
                    "scope" to "api://$targetAlias/$scope", // .default
                    "client_secret" to clientSecret,
                    "grant_type" to "client_credentials"
                ).toBody()
            )

        val res = client(req)
        val jwt = res.extractAccessToken(targetAlias)
        serviceToken[targetAlias] = jwt
        return jwt
    }
}

fun Response.extractAccessToken(alias: String): JwtToken {
    try {
        return JwtToken(JSONObject(this.bodyString()).get("access_token").toString())
    } catch (e: Exception) {
        File("/tmp/failedStatusWhenExtracting").writeText(
            "Received $status when attempting token extraction from $alias"
        )
        File("/tmp/failedExtractBody").writeText(this.bodyString())
        TokenExchangeHandler.log.error { "Failed to fetch access token for $alias - ${this.bodyString()}" }
        throw AuthenticationException("Failed to fetch access token for $alias")
    }
}
