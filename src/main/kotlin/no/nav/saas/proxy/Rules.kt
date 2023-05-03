package no.nav.saas.proxy

import com.google.gson.Gson

public typealias RuleSet = Map<String, Map<String, List<String>>>
public typealias Rule = String

object Rules {
    // Parse json to RuleSet structure; Map<namespace, Map<app, rules>>
    fun parse(filePath: String) =
        Gson().fromJson<RuleSet>(Application::class.java.getResource(filePath).readText(), Map::class.java)
}
