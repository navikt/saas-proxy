package no.nav.saas.proxy.config

object ConfigUtil {

    fun isCurrentlyRunningOnNais(): Boolean {
        return System.getenv("NAIS_APP_NAME") != null
    }
}
