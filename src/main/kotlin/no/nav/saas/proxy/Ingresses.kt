package no.nav.saas.proxy

import com.google.gson.Gson

typealias IngressSet = Map<String, Map<String, String>>
typealias Ingress = String

object Ingresses {
    fun parse(filePath: String) =
        Gson().fromJson<IngressSet>(Application::class.java.getResource(filePath).readText(), Map::class.java)
}
