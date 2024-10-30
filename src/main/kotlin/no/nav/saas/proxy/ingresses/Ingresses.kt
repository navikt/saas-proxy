package no.nav.saas.proxy.ingresses

import com.google.gson.Gson
import no.nav.saas.proxy.Application

typealias IngressSet = Map<String, Map<String, String>>
typealias Ingress = String

object Ingresses {
    fun parse(filePath: String) =
        Gson().fromJson<IngressSet>(Application::class.java.getResource(filePath).readText(), Map::class.java)

    fun IngressSet.ingressOf(app: String, namespace: String): String? {
        return this[namespace]?.let { it[app] }
    }
}
