package no.nav.saas.proxy.teamlogs

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import no.nav.saas.proxy.Application

// Config as structured in json file (for human maintenance):
typealias LogOwnerTeam = String
typealias TargetKey = String // "namespace.app"

data class LoggingConfig(
    val requestBody: Boolean = false,
    val responseBody: Boolean = false,
    val headers: List<String> = emptyList(),
)

data class TeamLoggingConfig(
    val gcpProject: String,
    val integrations: Map<TargetKey, LoggingConfig>,
)

typealias TeamLoggingSet = Map<LogOwnerTeam, TeamLoggingConfig>

// Config structured for relevant lookup:
data class EffectiveLoggingConfig(
    val gcpProject: String,
    val requestBody: Boolean,
    val responseBody: Boolean,
    val headers: List<String>,
)

typealias LoggingLookup = Map<TargetKey, EffectiveLoggingConfig>

object TeamLogging {
    private val gson = Gson()

    fun parse(filePath: String): TeamLoggingSet {
        val type = object : TypeToken<Map<String, Map<String, Any>>>() {}.type

        val raw: Map<String, Map<String, Any>> =
            gson.fromJson(
                Application::class.java.getResource(filePath)!!.readText(),
                type,
            )

        return raw.mapValues { (_, teamConfigRaw) ->
            val gcpProject =
                teamConfigRaw["gcpProject"] as? String
                    ?: error("Missing gcpProject in team logging config")

            val integrations =
                teamConfigRaw
                    .filterKeys { it != "gcpProject" }
                    .mapValues { (_, value) ->
                        gson.fromJson(
                            gson.toJson(value),
                            LoggingConfig::class.java,
                        )
                    }

            TeamLoggingConfig(
                gcpProject = gcpProject,
                integrations = integrations,
            )
        }
    }
}

fun TeamLoggingSet.toLookup(): LoggingLookup =
    buildMap {
        this@toLookup.forEach { (teamName, teamConfig) ->
            teamConfig.integrations.forEach { (appKey, loggingConfig) ->

                require(appKey.isNotBlank()) {
                    "Invalid empty appKey in team $teamName"
                }

                if (containsKey(appKey)) {
                    error("Duplicate logging config for '$appKey' (defined by multiple teams, including $teamName)")
                }

                put(
                    appKey,
                    EffectiveLoggingConfig(
                        gcpProject = teamConfig.gcpProject,
                        requestBody = loggingConfig.requestBody,
                        responseBody = loggingConfig.responseBody,
                        headers = loggingConfig.headers,
                    ),
                )
            }
        }
    }
