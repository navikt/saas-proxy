package no.nav.saas.proxy

import no.nav.saas.proxy.teamlogs.LoggingLookup
import no.nav.saas.proxy.teamlogs.TeamLogging
import no.nav.saas.proxy.teamlogs.toLookup
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

const val TEAMLOGGING_DEV = "/team-logging/dev.json"
const val TEAMLOGGING_PROD = "/team-logging/prod.json"

class TeamLoggingTest {
    val devTeamLogging = TeamLogging.parse(TEAMLOGGING_DEV)
    val prodTeamLogging = TeamLogging.parse(TEAMLOGGING_PROD)

    val devTeamLoggingLookup = devTeamLogging.toLookup()
    val prodTeamLoggingLookup = prodTeamLogging.toLookup()

    @Test
    fun `Test that team logging parsing do not break`() {
        devTeamLoggingLookup.size
        prodTeamLoggingLookup.size
        // println(devTeamLoggingLookup)
    }

    @Test
    fun `Ensure that tokens are not configured to be sent to team logs`() {
        Assertions.assertFalse(devTeamLoggingLookup.values.any { it.headers.map { it.lowercase().trim() }.contains("authorization") })
        Assertions.assertFalse(prodTeamLoggingLookup.values.any { it.headers.map { it.lowercase().trim() }.contains("authorization") })
    }
}
