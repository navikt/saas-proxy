package no.nav.saas.proxy

const val env_AZURE_APP_WELL_KNOWN_URL = "AZURE_APP_WELL_KNOWN_URL"
const val env_AZURE_APP_CLIENT_ID = "AZURE_APP_CLIENT_ID"
const val env_AZURE_APP_CLIENT_SECRET = "AZURE_APP_CLIENT_SECRET"
const val env_AZURE_OPENID_CONFIG_TOKEN_ENDPOINT = "AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"

const val env_REDIS_URI_SAASPROXY = "REDIS_URI_SAASPROXY"
const val env_REDIS_USERNAME_SAASPROXY = "REDIS_USERNAME_SAASPROXY"
const val env_REDIS_PASSWORD_SAASPROXY = "REDIS_PASSWORD_SAASPROXY"

const val env_NAIS_CLUSTER_NAME = "NAIS_CLUSTER_NAME"

const val config_WHITELIST_FILE = "WHITELIST_FILE"
const val config_INGRESS_FILE = "INGRESS_FILE"

/**
 * Shortcut for fetching environment variables
 */
fun env(name: String): String = System.getenv(name) ?: throw NullPointerException("Missing env $name")
