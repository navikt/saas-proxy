package no.nav.saas.proxy

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter
import io.prometheus.client.Gauge
import io.prometheus.client.hotspot.DefaultExports

object Metrics {

    val cRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val apiCalls: Gauge = registerLabelGauge("api_calls", "path")

    val testApiCalls: Gauge = registerLabelGauge("test_api_calls", "path")

    val oboCacheSize: Gauge = registerGauge("obo_cache_size")

    private val forwardedCalls: Counter =
        registerLabelCounter("forwarded_calls", "target_app", "path", "ingress", "token_type", "status")

    fun forwardedCallsInc(targetApp: String, path: String, ingress: String, tokenType: String, status: String) =
        forwardedCalls.labels(targetApp, path, ingress, tokenType, status).inc()

    fun registerGauge(name: String): Gauge {
        return Gauge.build().name(name).help(name).register()
    }

    fun registerLabelGauge(name: String, vararg labels: String): Gauge {
        return Gauge.build().name(name).help(name).labelNames(*labels).register()
    }

    fun registerLabelCounter(name: String, vararg labels: String): Counter {
        return Counter.build().name(name).help(name).labelNames(*labels).register()
    }

    init {
        DefaultExports.initialize()
    }
}
