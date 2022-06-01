package no.nav.saas.proxy

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Gauge
import io.prometheus.client.hotspot.DefaultExports

object Metrics {

    val cRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    val apiCalls: Gauge = registerLabelGauge("api_calls", "ingress")

    val testApiCalls: Gauge = registerLabelGauge("test_api_calls", "ingress")

    fun registerGauge(name: String): Gauge {
        return Gauge.build().name(name).help(name).register()
    }

    fun registerLabelGauge(name: String, label: String): Gauge {
        return Gauge.build().name(name).help(name).labelNames(label).register()
    }

    init {
        DefaultExports.initialize()
    }
}
