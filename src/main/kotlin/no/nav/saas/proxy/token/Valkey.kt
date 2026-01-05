package no.nav.saas.proxy.token

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import mu.KotlinLogging
import no.nav.saas.proxy.env
import no.nav.saas.proxy.env_VALKEY_HOST_SAASPROXY
import no.nav.saas.proxy.env_VALKEY_PASSWORD_SAASPROXY
import no.nav.saas.proxy.env_VALKEY_PORT_SAASPROXY
import no.nav.saas.proxy.env_VALKEY_USERNAME_SAASPROXY
import java.time.Instant
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

    fun updateAppLastSeen(
        appName: String,
        namespace: String,
        ttlSeconds: Long = 90 * 24 * 60 * 60,
    ) { // default 90 days
        try {
            val currentTimestamp = Instant.now().epochSecond
            val redisKey = "last_seen:$namespace:$appName"

            // Store timestamp with TTL
            commands.setex(redisKey, ttlSeconds, currentTimestamp.toString())

            log.info("Updated last seen for $namespace $appName to $currentTimestamp with TTL $ttlSeconds seconds")
        } catch (e: Exception) {
            log.error("Error updating last seen for $namespace $appName: ${e.message}")
        }
    }

    fun fetchAllLastSeen(): Map<String, MutableMap<String, Long>> {
        val result = mutableMapOf<String, MutableMap<String, Long>>()

        var cursor: ScanCursor = ScanCursor.INITIAL
        val scanArgs =
            ScanArgs.Builder
                .matches("last_seen:*")
                .limit(500)

        do {
            val scanResult = commands.scan(cursor, scanArgs)
            cursor = scanResult

            scanResult.keys.forEach { key ->
                // key format: last_seen:<namespace>:<appName>
                val parts = key.split(":")
                if (parts.size >= 3) {
                    val namespace = parts[1]
                    val appName = parts[2]

                    commands.get(key)?.toLongOrNull()?.let { timestamp ->
                        val nsMap = result.getOrPut(namespace) { mutableMapOf() }
                        nsMap[appName] = timestamp
                    }
                }
            }
        } while (!cursor.isFinished)

        return result
    }
}
