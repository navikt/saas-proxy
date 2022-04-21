package no.nav.saas.proxy.config

import no.nav.saas.proxy.config.ConfigUtil.isCurrentlyRunningOnNais
import no.nav.saas.proxy.config.StringEnvVar.getEnvVar

data class Environment(
    val namespace: String = getEnvVar("NAIS_NAMESPACE"),
    val appnavn: String = "saas-proxy",
    val aivenBrokers: String = getEnvVar("KAFKA_BROKERS"),
    val aivenSchemaRegistry: String = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
    val securityConfig: SecurityConfig = SecurityConfig(isCurrentlyRunningOnNais())
)

data class SecurityConfig(
    val enabled: Boolean,

    val variables: SecurityVars? = if (enabled) {
        SecurityVars()
    } else {
        null
    }
)

data class SecurityVars(
    val aivenTruststorePath: String = getEnvVar("KAFKA_TRUSTSTORE_PATH"),
    val aivenKeystorePath: String = getEnvVar("KAFKA_KEYSTORE_PATH"),
    val aivenCredstorePassword: String = getEnvVar("KAFKA_CREDSTORE_PASSWORD"),
    val aivenSchemaRegistryUser: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
    val aivenSchemaRegistryPassword: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD")
)
