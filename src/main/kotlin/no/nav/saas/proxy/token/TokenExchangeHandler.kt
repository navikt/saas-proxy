package no.nav.saas.proxy.token

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.saas.proxy.Application
import no.nav.saas.proxy.Metrics
import no.nav.saas.proxy.env
import no.nav.saas.proxy.env_AZURE_APP_CLIENT_ID
import no.nav.saas.proxy.env_AZURE_APP_CLIENT_SECRET
import no.nav.saas.proxy.env_AZURE_OPENID_CONFIG_TOKEN_ENDPOINT
import no.nav.security.token.support.core.jwt.JwtToken
import org.apache.http.NoHttpResponseException
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.body.toBody
import org.json.JSONObject
import java.io.File
import java.lang.Exception
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.naming.AuthenticationException
import javax.net.ssl.SSLHandshakeException

object TokenExchangeHandler {
    /**
     * A handler for azure on-behalf-of exchange flow.
     * @see [v2_oauth2_on_behalf_of_flow](https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-on-behalf-of-flow)
     *
     * Exchanges an azure on-behalf-of-token with audience to this app for one with audience to salesforce. Caches the result
     */

    val log = KotlinLogging.logger { }

    private val azureClient = Application.clientAzure

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

        if (Redis.useMe) {
            /** The redis way */
            val millisBeforeRedisFetch = System.currentTimeMillis()
            val cachedResult = Redis.commands.get(key)
            Metrics.fetchTimeObserve(System.currentTimeMillis() - millisBeforeRedisFetch)
            if (cachedResult != null) {
                log.info { "Cache hit (Redis): Retrieved token result from cache." }
                return JwtToken(cachedResult)
            }
        } else {
            /** The legacy way */
            OBOcache[key]?.let { cachedToken ->
                if (cachedToken.jwtTokenClaims.expirationTime.toInstant().minusSeconds(10) > Instant.now()) {
                    log.info { "Cached exchange obo token $targetAlias" }
                    return cachedToken
                }
            }
            Metrics.cacheSize.set(OBOcache.size.toDouble())
        }

        log.info { "Exchange obo token $targetAlias" }

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
                            },
                            "roles": {
                                "essential": true
                            }
                         }
                    }"""
                ).toBody()
            )
        val res = clientCallWithRetries(req)

        val jwtEncoded = res.extractAccessToken(targetAlias, "obo", req)
        val jwt = JwtToken(jwtEncoded)

        if (Redis.useMe) {
            /** The redis way */
            updateRedisCache(jwt, jwtEncoded, key, jwtIn.tokenAsString, "obo")
        } else {
            /** The legacy way */
            OBOcache[key] = jwt
        }
        return jwt
    }

    fun acquireServiceToken(targetAlias: String, scope: String): JwtToken {
        if (Redis.useMe) {
            /** The redis way */
            val millisBeforeRedisFetch = System.currentTimeMillis()
            val cachedResult = Redis.commands.get(targetAlias)
            Metrics.fetchTimeObserve(System.currentTimeMillis() - millisBeforeRedisFetch)
            if (cachedResult != null) {
                log.info { "Cache hit (Redis) m2m: Retrieved token result from cache." }
                return JwtToken(cachedResult)
            }
        } else {
            /** The legacy way */
            serviceToken[targetAlias]?.let { cachedToken ->
                if (cachedToken.jwtTokenClaims.expirationTime.toInstant().minusSeconds(10) > Instant.now()) {
                    log.info { "Cached service obo token $targetAlias" }
                    return cachedToken
                }
            }
        }
        log.info { "Acquire service token $targetAlias" }
        val m2mscope = if (scope == "defaultaccess") ".default" else scope
        val req = Request(Method.POST, azureTokenEndPoint)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(
                listOf(
                    "client_id" to clientId,
                    "scope" to "api://$targetAlias/$m2mscope",
                    "client_secret" to clientSecret,
                    "grant_type" to "client_credentials"
                ).toBody()
            )

        val res = clientCallWithRetries(req)

        val jwtEncoded = res.extractAccessToken(targetAlias, "m2m", req)
        val jwt = JwtToken(jwtEncoded)

        if (Redis.useMe) {
            /** The redis way */
            updateRedisCache(jwt, jwtEncoded, targetAlias)
        } else {
            /** The legacy way */
            serviceToken[targetAlias] = jwt
        }
        return jwt
    }

    fun updateRedisCache(jwt: JwtToken, jwtEncoded: String, key: String, jwtIn: String = "N/A", lblType: String = "m2m") {
        val expireTime = jwt.jwtTokenClaims.expirationTime.toInstant()
        val secondsToLive = Duration.between(Instant.now(), expireTime).seconds
        val secondsToLiveInCache = secondsToLive - 3
        withLoggingContext(
            mapOf("exchange_token_ttl" to secondsToLive.toString())
        ) {
            if (secondsToLiveInCache > 3) {
                val millisBeforeRedisStore = System.currentTimeMillis()
                Redis.commands.setex(key, secondsToLiveInCache, jwtEncoded)
                Metrics.storeTimeObserve(System.currentTimeMillis() - millisBeforeRedisStore)
            } else {
                File("/tmp/badmargin-$lblType").writeText(
                    LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + "\n\n" +
                        "JwtIn:\n$jwtIn:\n\nJwtGotten:\n$jwtEncoded"
                )
                log.warn { "Skipping caching token that would have been stored less then 3 seconds" }
            }
        }
    }

    fun clientCallWithRetries(
        request: Request,
        maxRetries: Int = 3,
        delayMillis: Long = 100
    ): Response {
        var attempt = 0
        var lastException: Exception? = null

        while (attempt < maxRetries) {
            try {
                attempt++
                val response = azureClient(request)
                if (response.status.code == 504) {
                    log.warn { "Time out on attempt $attempt. Retrying..." }
                } else {
                    return response
                }
            } catch (e: SSLHandshakeException) {
                lastException = e
                log.warn { "SSL handshake failed on attempt $attempt. Retrying in ${delayMillis}ms..." }
                Thread.sleep(delayMillis)
            } catch (e: NoHttpResponseException) {
                lastException = e
                log.warn { "No Http Response fail on attempt $attempt. Retrying in ${delayMillis}ms..." }
                Thread.sleep(delayMillis)
            } catch (e: Exception) {
                lastException = e
                log.error { "Unexpected error on attempt $attempt: ${e.message}. Retrying in ${delayMillis}ms..." }
                Thread.sleep(delayMillis)
                // break // Exit loop for non-retriable exceptions
            }
        }

        throw lastException ?: RuntimeException("Failed to execute action after $maxRetries attempts.")
    }
}

fun Response.extractAccessToken(alias: String, tokenType: String, request: Request): String {
    try {
        return JSONObject(this.bodyString()).get("access_token").toString()
    } catch (e: Exception) {
        File("/tmp/failedExtractAccessToken-$alias-$tokenType").writeText(
            LocalDateTime.now().format(
                DateTimeFormatter.ISO_DATE_TIME
            ) + "\n\nREQUEST:\n" + request.toMessage() + "\n\nRESPONSE:\n" + this.toMessage()
        )
        Metrics.tokenFetchFail.labels(alias, tokenType).inc()
        TokenExchangeHandler.log.error { "Failed to fetch $tokenType access token for $alias - ${this.bodyString()}" }
        throw AuthenticationException("Failed to fetch $tokenType access token for $alias - ${this.bodyString()}")
    }
}
