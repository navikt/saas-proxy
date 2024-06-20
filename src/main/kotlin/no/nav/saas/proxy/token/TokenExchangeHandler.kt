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
import java.time.Instant

object TokenExchangeHandler {
    /**
     * A handler for azure on-behalf-of exchange flow.
     * @see [v2_oauth2_on_behalf_of_flow](https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-on-behalf-of-flow)
     *
     * Exchanges an azure on-behalf-of-token with audience to this app for one with audience to salesforce. Caches the result
     */

    private val log = KotlinLogging.logger { }

    private val client = Application.client

    private val clientId: String = env(env_AZURE_APP_CLIENT_ID)
    private val clientSecret: String = env(env_AZURE_APP_CLIENT_SECRET)
    private val sfClientId: String = env("sf_client_id")
    private val sfClientSecret: String = env("sf_client_secret")

    private val azureTokenEndPoint: String = env(env_AZURE_OPENID_CONFIG_TOKEN_ENDPOINT)

    private var serviceToken: MutableMap<String, JwtToken> = mutableMapOf() // alias, token out
    private var OBOSFcache: MutableMap<String, JwtToken> = mutableMapOf() // token in (alias + user), token out
    private var OBOcache: MutableMap<String, JwtToken> = mutableMapOf() // token in (alias + user), token out

    private var droppedCacheElements = 0L

    fun refreshCache() {
        OBOcache = OBOcache.filterValues {
            val stillEligable = it.jwtTokenClaims.expirationTime.toInstant().minusSeconds(10) > Instant.now()
            if (!stillEligable) droppedCacheElements++
            stillEligable
        }.toMutableMap()
        log.info { "Dropped cache elements during lifetime $droppedCacheElements" }
    }

    fun isOBOToken(jwt: JwtToken) = jwt.jwtTokenClaims.get("NAVident") != null

    // target alias example: cluster.namespace.app
    fun exchange(jwtIn: JwtToken, targetAlias: String): JwtToken {
        if (!isOBOToken(jwtIn)) return acquireServiceToken(targetAlias)
        log.info { "Exchange obo token $targetAlias" }
        val key = jwtIn.tokenAsString
        OBOcache[key]?.let { cachedToken ->
            if (cachedToken.jwtTokenClaims.expirationTime.toInstant().minusSeconds(10) > Instant.now()) {
                return cachedToken
            }
        }
        log.info { "Fetch Exchange obo token $targetAlias" }
        Metrics.oboCacheSize.set(OBOcache.size.toDouble())

        val req = Request(Method.POST, azureTokenEndPoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(
                listOf(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
                    "assertion" to jwtIn.tokenAsString,
                    "client_id" to clientId,
                    "scope" to "api://$targetAlias/.default",
                    "client_secret" to clientSecret,
                    "requested_token_use" to "on_behalf_of"
                ).toBody()
            )

        lateinit var res: Response
        // tokenFetchStats.elapsedTimeOboExchangeRequest = measureTimeMillis {
        res = client(req)

        File("/tmp/exchangerequest").writeText(req.toMessage())
        File("/tmp/exchangeresponse").writeText(res.toMessage())
        // }
        val jwt = JwtToken(JSONObject(res.bodyString()).get("access_token").toString())
        OBOcache[key] = jwt
        return jwt
    }

    fun acquireServiceToken(targetAlias: String): JwtToken {
        log.info { "Acquire service token $targetAlias" }
        serviceToken[targetAlias]?.let { cachedToken ->
            if (cachedToken.jwtTokenClaims.expirationTime.toInstant().minusSeconds(10) > Instant.now()) {
                return cachedToken
            }
        }
        log.info { "Fetch Acquire service token $targetAlias" }
        val req = Request(Method.POST, azureTokenEndPoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(
                listOf(
                    "client_id" to clientId,
                    "scope" to "api://$targetAlias/.default",
                    "client_secret" to clientSecret,
                    "grant_type" to "client_credentials"
                ).toBody()
            )
        lateinit var res: Response
        res = client(req)
        val jwt = JwtToken(JSONObject(res.bodyString()).get("access_token").toString())
        serviceToken[targetAlias] = jwt
        return jwt
    }
}
