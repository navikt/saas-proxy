package no.nav.saas.proxy.token

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.StaticCredentialsProvider
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import no.nav.saas.proxy.env
import no.nav.saas.proxy.env_REDIS_PASSWORD_SAASPROXY
import no.nav.saas.proxy.env_REDIS_URI_SAASPROXY
import no.nav.saas.proxy.env_REDIS_USERNAME_SAASPROXY

object Redis {
    const val useMe = true

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
}
