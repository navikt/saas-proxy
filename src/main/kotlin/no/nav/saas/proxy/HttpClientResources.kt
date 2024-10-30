package no.nav.saas.proxy

import mu.KotlinLogging
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.pool.PoolStats
import org.http4k.client.ApacheClient
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object HttpClientResources {
    private val log = KotlinLogging.logger { }

    private const val client_DOWNSTREAM = "downstream"
    private const val client_TOKEN = "token"

    private val connectionManager = PoolingHttpClientConnectionManager().apply {
        maxTotal = 20
        defaultMaxPerRoute = 10
    }

    private val azureConnectionManager = PoolingHttpClientConnectionManager().apply {
        maxTotal = 10
        defaultMaxPerRoute = 5
    }

    // Http client used for calls downstream
    private val httpClient: CloseableHttpClient = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectTimeout(60000)
                .setSocketTimeout(60000)
                .setConnectionRequestTimeout(60000)
                .setRedirectsEnabled(false)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                .build()
        ).build()

    // Http client used for calls to Azure/Entra to fetch tokens. Shorter timeouts
    private val azureHttpClient: CloseableHttpClient = HttpClients.custom()
        .setConnectionManager(azureConnectionManager)
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectTimeout(5000)
                .setSocketTimeout(5000)
                .setConnectionRequestTimeout(3000)
                .setRedirectsEnabled(false)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                .build()
        ).build()

    val client = ApacheClient(httpClient)
    val clientAzure = ApacheClient(azureHttpClient)

    fun scheduleConnectionMetricsUpdater() {
        log.info { "Schedule connection metrics updater" }
        executor.scheduleAtFixedRate(
            {
                updateConnectionMetrics(connectionManager, client_DOWNSTREAM)
                updateConnectionMetrics(azureConnectionManager, client_TOKEN)
            },
            0, 10, TimeUnit.SECONDS
        )
    }

    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    private fun updateConnectionMetrics(connectionManager: PoolingHttpClientConnectionManager, clientLabel: String) {
        val stats: PoolStats = connectionManager.totalStats
        Metrics.activeConnections.labels(clientLabel).set(stats.leased.toDouble())
        if (stats.leased.toDouble() > Metrics.activeConnectionsMax.labels(clientLabel).get()) {
            Metrics.activeConnectionsMax.labels(clientLabel).set(stats.leased.toDouble())
        }
        Metrics.idleConnections.labels(clientLabel).set(stats.available.toDouble())
        Metrics.maxConnections.labels(clientLabel).set(stats.max.toDouble())
        Metrics.pendingConnections.labels(clientLabel).set(stats.pending.toDouble())
    }
}
