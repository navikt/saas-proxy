package no.nav.saas.proxy.token

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import mu.KotlinLogging
import no.nav.saas.proxy.env
import no.nav.saas.proxy.env_VALKEY_HOST_SAASPROXY
import no.nav.saas.proxy.env_VALKEY_PASSWORD_SAASPROXY
import no.nav.saas.proxy.env_VALKEY_PORT_SAASPROXY
import no.nav.saas.proxy.env_VALKEY_USERNAME_SAASPROXY
import kotlin.system.measureTimeMillis

object Valkey {
    private val log = KotlinLogging.logger { }

    var initialCheckPassed = false

    fun isReady(): Boolean =
        if (initialCheckPassed) {
            true
        } else {
            try {
                val queryTime =
                    measureTimeMillis {
                        commands.get("dummy")
                    }
                log.info { "Initial check query time $queryTime ms" }
                if (queryTime < 100) {
                    initialCheckPassed = true
                }
                false
            } catch (e: java.lang.Exception) {
                log.error { e.printStackTrace() }
                false
            }
        }

    fun connect(): RedisCommands<String, String> {
        val redisURI =
            RedisURI.Builder
                .redis(env(env_VALKEY_HOST_SAASPROXY), env(env_VALKEY_PORT_SAASPROXY).toInt())
                .withSsl(true)
                .withAuthentication(env(env_VALKEY_USERNAME_SAASPROXY), env(env_VALKEY_PASSWORD_SAASPROXY).toCharArray())
                .build()

        val client: RedisClient = RedisClient.create(redisURI)

        val connection: StatefulRedisConnection<String, String> = client.connect()
        return connection.sync()
    }

    val commands = connect()
}
