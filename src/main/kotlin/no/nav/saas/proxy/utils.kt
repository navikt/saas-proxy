@file:Suppress("ktlint:standard:filename")

package no.nav.saas.proxy

import no.nav.saas.proxy.Application.cluster
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val currentDateTime: String get() = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

/**
 * targetCluster - resolves target cluster based on the cluster of the proxy (dev or prod)
 *                 with gcp replaced with fss if we are targeting an ingress
 */
fun targetCluster(specifiedIngress: String?) =
    specifiedIngress?.let {
        cluster.replace("gcp", "fss")
    } ?: cluster
