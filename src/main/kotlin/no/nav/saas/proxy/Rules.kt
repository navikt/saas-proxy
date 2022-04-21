package no.nav.saas.proxy

import com.google.gson.Gson

object Rules {
    fun parse(filePath: String) =
        Gson().fromJson<Map<String, Map<String, List<String>>>>(Application::class.java.getResource(filePath).readText(), Map::class.java)
}
