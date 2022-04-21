package no.nav.saas.proxy

import io.prometheus.client.Gauge

class Metrics {
    val preStopHook: Gauge = Gauge
        .build()
        .name("pre_stop__hook_gauge")
        .help("No. of preStopHook activations since last restart")
        .register()
    val requestsDone: Gauge = Gauge
        .build()
        .name("request_done")
        .help("request_done")
        .register()
    val requestsInnboks: Gauge = Gauge
        .build()
        .name("request_innboks")
        .help("request_innboks")
        .register()
    val apiIssues: Gauge = Gauge
        .build()
        .name("api_issues")
        .help("api_issues")
        .register()
}

val metrics = Metrics()
