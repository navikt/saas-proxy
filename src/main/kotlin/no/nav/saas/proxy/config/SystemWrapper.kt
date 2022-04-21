package no.nav.saas.proxy.config

internal object SystemWrapper {
    fun getEnvVar(varName: String): String? {
        return System.getenv(varName)
    }
}
