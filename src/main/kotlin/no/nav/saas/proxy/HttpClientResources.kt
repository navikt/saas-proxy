package no.nav.saas.proxy

import mu.KotlinLogging
import okhttp3.OkHttpClient
import org.http4k.client.OkHttp
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

object HttpClientResources {
    private val log = KotlinLogging.logger { }

    private const val CLIENT_DOWNSTREAM = "downstream"
    private const val CLIENT_TOKEN = "token"

    // OkHttpClient for downstream with longer timeouts
    private val httpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(Duration.ofSeconds(60))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))
            .retryOnConnectionFailure(false)
            .build()

    // OkHttpClient for Azure/Entra token calls with shorter timeouts
    private val azureHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(5))
            .writeTimeout(Duration.ofSeconds(3))
            .retryOnConnectionFailure(false)
            .build()

    val client = OkHttp(httpClient)
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
