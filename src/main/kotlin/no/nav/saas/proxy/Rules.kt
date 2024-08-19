package no.nav.saas.proxy

import com.google.gson.Gson

typealias RuleSet = Map<String, Map<String, List<String>>> // Map of namespaces, with map of apps, with list of patterns
typealias Rule = String

object Rules {
    fun parse(filePath: String) =
        Gson().fromJson<RuleSet>(Application::class.java.getResource(filePath).readText(), Map::class.java)
}
