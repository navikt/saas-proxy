package no.nav.saas.proxy.token

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import mu.KotlinLogging
import no.nav.saas.proxy.env
import no.nav.saas.proxy.env_REDIS_PASSWORD_SAASPROXY
import no.nav.saas.proxy.env_REDIS_URI_SAASPROXY
import no.nav.saas.proxy.env_REDIS_USERNAME_SAASPROXY

object Redis {

    private val log = KotlinLogging.logger { }

    // Function to connect to Redis using the environment variables
    fun connectToRedis(): RedisCommands<String, String> {
        // Read the environment variables for the Redis instance
        val redisUri = env(env_REDIS_URI_SAASPROXY)
        val redisUsername = env(env_REDIS_USERNAME_SAASPROXY)
        val redisPassword = env(env_REDIS_PASSWORD_SAASPROXY)

        // Create a Redis URI object
        val uri = RedisURI.create(redisUri).apply {
            if (!redisUsername.isNullOrEmpty()) {
                this.username = redisUsername
            }
            if (!redisPassword.isNullOrEmpty()) {
                this.password = redisPassword.toCharArray()
            }
        }

        // Create a Redis client and connect
        val client: RedisClient = RedisClient.create(uri)
        val connection: StatefulRedisConnection<String, String> = client.connect()
        return connection.sync()
    }

    val commands = connectToRedis()
}
