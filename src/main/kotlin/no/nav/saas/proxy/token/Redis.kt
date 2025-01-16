package no.nav.saas.proxy.token

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.StaticCredentialsProvider
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.saas.proxy.Metrics
import no.nav.saas.proxy.env
import no.nav.saas.proxy.env_REDIS_PASSWORD_SAASPROXY
import no.nav.saas.proxy.env_REDIS_URI_SAASPROXY
import no.nav.saas.proxy.env_REDIS_USERNAME_SAASPROXY
import kotlin.system.measureTimeMillis

object Redis {

    private val log = KotlinLogging.logger { }

    var initialCheckPassed = false

    fun isReady(): Boolean {
        return if (initialCheckPassed) {
            true
        } else {
            var response: Long
            val queryTime = measureTimeMillis {
                response = dbSize()
            }
            log.info { "Initial check query time $queryTime ms (got count $response)" }
            if (queryTime < 100) {
                initialCheckPassed = true
            }
            false
        }
    }

    fun connectToRedis(): RedisCommands<String, String> {
        val staticCredentialsProvider = StaticCredentialsProvider(
            env(env_REDIS_USERNAME_SAASPROXY),
            env(env_REDIS_PASSWORD_SAASPROXY).toCharArray()
        )

        val redisURI = RedisURI.create(env(env_REDIS_URI_SAASPROXY)).apply {
            this.credentialsProvider = staticCredentialsProvider
        }

        val client: RedisClient = RedisClient.create(redisURI)
        val connection: StatefulRedisConnection<String, String> = client.connect()
        return connection.sync()
    }

    val commands = connectToRedis()

    fun dbSize(): Long = commands.dbsize()

    tailrec fun cacheQueryLoop() {
        runBlocking { delay(60000) } // 1 min
        try {
            Metrics.cacheSize.set(Redis.dbSize().toDouble())
        } catch (e: Exception) {
            log.warn { "Failed to query Redis dbSize" }
        }
        runBlocking { delay(840000) } // 14 min
        cacheQueryLoop()
    }
}
