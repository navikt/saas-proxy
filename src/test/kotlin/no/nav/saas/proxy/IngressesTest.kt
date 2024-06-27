package no.nav.saas.proxy

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

const val INGRESSES_DEV = "/ingresses/dev.json"
const val INGRESSES_PROD = "/ingresses/prod.json"

class IngressesTest {
    val devIngressesSet = Ingresses.parse(INGRESSES_DEV)
    val prodIngressesSet = Ingresses.parse(INGRESSES_PROD)

    val ingressesSet = setOf(devIngressesSet, prodIngressesSet)

    @Test
    fun test_that_parsing_dev_ingresses_contains_known_ingress() {
        devIngressesSet.let {
            val knownNamespace = "teamcrm"
            val knownApp = "sf-arkiv"
            Assertions.assertTrue(it.containsKey(knownNamespace))
            Assertions.assertTrue(it[knownNamespace]!!.containsKey(knownApp))
            Assertions.assertTrue(it[knownNamespace]!![knownApp]!!.contains("https://sf-arkiv-dokumentasjon.dev-fss-pub.nais.io"))
        }
    }

    @Test
    fun test_that_parsing_prod_ingresses_contains_known_ingress() {
        prodIngressesSet.let {
            val knownNamespace = "teamcrm"
            val knownApp = "sf-arkiv"
            Assertions.assertTrue(it.containsKey(knownNamespace))
            Assertions.assertTrue(it[knownNamespace]!!.containsKey(knownApp))
            Assertions.assertTrue(it[knownNamespace]!![knownApp]!!.contains("https://sf-arkiv-dokumentasjon.prod-fss-pub.nais.io"))
        }
    }

    @Test
    fun test_that_all_ingresses_starts_with_https() {
        ingressesSet.forEach { ingressSet ->
            ingressSet.values.forEach { app ->
                app.values.forEach { ingress ->
                    Assertions.assertTrue(ingress.startsWith("https://"))
                }
            }
        }
    }
}
