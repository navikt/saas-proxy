package no.nav.saas.proxy

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import io.prometheus.client.Summary
import io.prometheus.client.hotspot.DefaultExports

object Metrics {

    val cRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val apiCalls: Gauge = registerLabelGauge("api_calls", "target_app", "path")

    val tokenFetchFail: Counter = registerLabelCounter("token_fetch_fail", "target_alias", "token_type")

    val noAuth: Counter = registerLabelCounter("no_auth", "target_app")

    val testApiCalls: Gauge = registerLabelGauge("test_api_calls", "path")

    val cacheSize: Gauge = registerGauge("cache_size")

    private val cacheFetchTime: Summary = registerSummary("cache_fetch_time")
    private val cacheFetchTimeMax: Gauge = registerGauge("cache_fetch_time_max")

    private val cacheStoreTime: Summary = registerSummary("cache_store_time")
    private val cacheStoreTimeMax: Gauge = registerGauge("cache_store_time_max")

    private val forwardedCalls: Counter =
        registerLabelCounter("forwarded_calls", "target_app", "path", "ingress", "token_type", "status")

    val totalMsHistogram = registerForwardedCallHistogram("total_ms")

    val handlingMsHistogram = registerForwardedCallHistogram("handling_ms")

    val redirectMsHistogram = registerForwardedCallHistogram("redirect_ms")

    val activeConnections: Gauge = registerLabelGauge("connections_active", "client")

    val idleConnections: Gauge = registerLabelGauge("connections_idle", "client")

    val maxConnections: Gauge = registerLabelGauge("connections_max", "client")

    val pendingConnections: Gauge = registerLabelGauge("connections_pending", "client")

    fun fetchTimeObserve(durationMillis: Long) {
        cacheFetchTime.observe(durationMillis.toDouble())
        if (durationMillis > cacheFetchTimeMax.get()) cacheFetchTimeMax.set(durationMillis.toDouble())
    }

    fun storeTimeObserve(durationMillis: Long) {
        cacheStoreTime.observe(durationMillis.toDouble())
        if (durationMillis > cacheStoreTimeMax.get()) cacheStoreTimeMax.set(durationMillis.toDouble())
    }

    fun forwardedCallsInc(
        targetApp: String,
        path: String,
        ingress: String,
        tokenType: String,
        status: String,
        totalMs: Long,
        handlingMs: Long,
        redirectMs: Long
    ) {
        forwardedCalls.labels(targetApp, path, ingress, tokenType, status).inc()
        totalMsHistogram.labels(targetApp, tokenType, status).observe(totalMs.toDouble())
        handlingMsHistogram.labels(targetApp, tokenType, status).observe(handlingMs.toDouble())
        redirectMsHistogram.labels(targetApp, tokenType, status).observe(redirectMs.toDouble())
    }

    fun registerForwardedCallHistogram(name: String): Histogram {
        return Histogram.build().name(name).help(name)
            .labelNames("targetApp", "tokenType", "status")
            .buckets(50.0, 100.0, 200.0, 300.0, 400.0, 500.0, 1000.0, 2000.0, 4000.0)
            .register()
    }

    fun registerSummary(name: String) = Summary.build().name(name).help(name).register()

    fun registerGauge(name: String) =
        Gauge.build().name(name).help(name).register()

    fun registerLabelGauge(name: String, vararg labels: String) =
        Gauge.build().name(name).help(name).labelNames(*labels).register()

    fun registerLabelCounter(name: String, vararg labels: String) =
        Counter.build().name(name).help(name).labelNames(*labels).register()

    /**
     * Mask common path variables to avoid separate counts for paths with varying segments.
     */
    fun mask(path: String): String =
        path.replace(Regex("/\\d+"), "/{id}")
            .replace(Regex("/[A-Z]\\d{4,}"), "/{ident}")
            .replace(Regex("/[^/]+\\.(xml|pdf)$"), "/{filename}")
            .replace(Regex("/[A-Z]{3}(?=/|$)"), "/{code}")

    init {
        DefaultExports.initialize()
    }
}
