package no.nav.saas.proxy

import mu.KotlinLogging
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.http4k.client.OkHttp
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object HttpClientResources {
    private val log = KotlinLogging.logger { }

    private const val CLIENT_DOWNSTREAM = "downstream"
    private const val CLIENT_TOKEN = "token"

    // OkHttpClient for downstream with longer timeouts
    private val httpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectionPool(ConnectionPool(5, 60, TimeUnit.SECONDS))
            .connectTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .retryOnConnectionFailure(false)
            .build()

    private val httpClientRetry: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectionPool(ConnectionPool(5, 60, TimeUnit.SECONDS))
            .connectTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .retryOnConnectionFailure(true)
            .build()

    // OkHttpClient for Azure/Entra token calls with shorter timeouts
    private val azureHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectionPool(ConnectionPool(5, 60, TimeUnit.SECONDS))
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(5))
            .writeTimeout(Duration.ofSeconds(3))
            .retryOnConnectionFailure(true)
            .build()

    val client = OkHttp(httpClient)
    val clientRetry = OkHttp(httpClientRetry)
    val clientAzure = OkHttp(azureHttpClient)

    fun scheduleConnectionMetricsUpdater() {
//        log.info { "Schedule connection metrics updater (note: limited insight for OkHttp)" }
//        executor.scheduleAtFixedRate(
//            {
//                // No direct equivalent for PoolStats — optionally collect stats if using custom EventListener
//                log.debug { "Connection stats not available with OkHttp without additional tooling." }
//            },
//            0, 10, TimeUnit.SECONDS
//        )
    }

    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
}
