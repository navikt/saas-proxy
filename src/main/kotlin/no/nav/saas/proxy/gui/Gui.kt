package no.nav.saas.proxy.gui

import com.google.gson.GsonBuilder
import no.nav.saas.proxy.Application.ruleSet
import no.nav.saas.proxy.Application.startedAt
import no.nav.saas.proxy.token.Valkey
import no.nav.saas.proxy.whitelist.RuleSet
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

object Gui {
    val startedAtHandler: HttpHandler = {
        Response(OK)
            .header("Content-Type", "application/json")
            .body("""{"startedAt": ${startedAt.epochSecond}}""")
    }

    val lastSeenHandler: HttpHandler = {
        val data = buildNamespaceAppData(Valkey.fetchAllLastSeen(), ruleSet) // returns Map<String, Map<String, Long>>
        val gson =
            GsonBuilder()
                .serializeNulls()
                .create()
        Response(OK)
            .header("Content-Type", "application/json")
            .body(gson.toJson(data))
    }

    fun buildNamespaceAppData(
        redisData: Map<String, Map<String, Long>>, // last seen from Valkey
        ruleSet: RuleSet, // full whitelist config
    ): Map<String, Map<String, Long?>> { // namespace -> app -> lastSeen (nullable)

        val result = mutableMapOf<String, MutableMap<String, Long?>>()

        for ((namespace, apps) in ruleSet) {
            val nsMap = mutableMapOf<String, Long?>()
            for ((app, _) in apps) {
                // take value from Redis if exists, otherwise null
                val lastSeen = redisData[namespace]?.get(app)
                nsMap[app] = lastSeen
            }
            result[namespace] = nsMap
        }

        // Include any Redis-only apps (if not in config)
        redisData.forEach { (namespace, apps) ->
            val nsMap = result.getOrPut(namespace) { mutableMapOf() }
            apps.forEach { (app, lastSeen) ->
                nsMap.putIfAbsent(app, lastSeen)
            }
        }

        return result
    }
}
