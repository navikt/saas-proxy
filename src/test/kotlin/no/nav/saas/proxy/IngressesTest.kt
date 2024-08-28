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
    fun `Test that parsing dev ingresses contains known ingress`() {
        devIngressesSet.let {
            val knownNamespace = "teamcrm"
            val knownApp = "sf-arkiv"
            Assertions.assertTrue(it.containsKey(knownNamespace))
            Assertions.assertTrue(it[knownNamespace]!!.containsKey(knownApp))
            Assertions.assertTrue(it[knownNamespace]!![knownApp]!!.contains("https://sf-arkiv-dokumentasjon.dev-fss-pub.nais.io"))
        }
    }

    @Test
    fun `Test that parsing prod ingresses contains known ingress`() {
        prodIngressesSet.let {
            val knownNamespace = "teamcrm"
            val knownApp = "sf-arkiv"
            Assertions.assertTrue(it.containsKey(knownNamespace))
            Assertions.assertTrue(it[knownNamespace]!!.containsKey(knownApp))
            Assertions.assertTrue(it[knownNamespace]!![knownApp]!!.contains("https://sf-arkiv-dokumentasjon.prod-fss-pub.nais.io"))
        }
    }

    @Test
    fun `Test that all ingresses starts with https`() {
        ingressesSet.forEach { ingressSet ->
            ingressSet.values.forEach { app ->
                app.values.forEach { ingress ->
                    Assertions.assertTrue(ingress.startsWith("https://"))
                }
            }
        }
    }

    @Test
    fun `Make sure that no prod ingress point at dev-fss (typical copy paste mistake)`() {
        prodIngressesSet.values.forEach { app ->
            app.values.forEach { ingress ->
                Assertions.assertFalse(ingress.contains("dev-fss"))
            }
        }
    }
}
