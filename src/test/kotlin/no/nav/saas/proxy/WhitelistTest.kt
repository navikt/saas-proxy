package no.nav.saas.proxy
import no.nav.saas.proxy.whitelist.RuleSet
import no.nav.saas.proxy.whitelist.Whitelist
import no.nav.saas.proxy.whitelist.Whitelist.evaluateAsRule
import no.nav.saas.proxy.whitelist.Whitelist.findScope
import no.nav.saas.proxy.whitelist.Whitelist.namespaceOfApp
import no.nav.saas.proxy.whitelist.Whitelist.rulesOf
import org.http4k.core.Method
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.IllegalStateException

const val WHITELIST_DEV = "/whitelist/dev.json"
const val WHITELIST_PROD = "/whitelist/prod.json"

class WhitelistTest {
    private val devRuleSet: RuleSet = Whitelist.parse(WHITELIST_DEV)
    private val prodRuleSet: RuleSet = Whitelist.parse(WHITELIST_PROD)

    private val rulesSet = setOf(devRuleSet, prodRuleSet)

    @Test
    fun `Test that parsing dev whitelist contains known rule`() {
        devRuleSet.let {
            val knownNamespace = "teamnks"
            val knownApp = "sf-brukernotifikasjon"
            assertTrue(it.containsKey(knownNamespace))
            assertTrue(it[knownNamespace]!!.containsKey(knownApp))
            assertTrue(it[knownNamespace]!![knownApp]!!.contains("POST /done"))
        }
    }

    @Test
    fun `Test lookup function for namespace returns existing namespace or null`() {
        val knownNamespace = "teamnks"
        val knownApp = "sf-brukernotifikasjon"
        assertTrue(devRuleSet.namespaceOfApp(knownApp) == knownNamespace)
        assertTrue(devRuleSet.namespaceOfApp("NON_EXISTING") == null)
    }

    @Test
    fun `Test lookup function for rules returns list of rules or empty list`() {
        val knownNamespace = "teamnks"
        val knownApp = "sf-brukernotifikasjon"
        assertTrue(devRuleSet.rulesOf(knownApp, knownNamespace).isNotEmpty())
        assertTrue(devRuleSet.rulesOf("NON_EXISTING", knownNamespace).isEmpty())
        assertTrue(devRuleSet.rulesOf(knownApp, "NON_EXISTING").isEmpty())
    }

    @Test
    fun `Test lookup function for namespace throws exception if app is found in two namespaces in ruleset`() {
        val knownApp = "sf-brukernotifikasjon"
        val devRuleSetWithTwinAppInAnotherNamespace = devRuleSet + mapOf("Other namespace" to mapOf(knownApp to listOf("Other Rule")))
        assertThrows(IllegalStateException::class.java) {
            devRuleSetWithTwinAppInAnotherNamespace.namespaceOfApp(knownApp)
        }
    }

    @Test
    fun `Test that parsing prod whitelist contains known rule`() {
        prodRuleSet.let {
            val knownNamespace = "teamnks"
            val knownApp = "sf-brukernotifikasjon"
            assertTrue(it.containsKey(knownNamespace))
            assertTrue(it[knownNamespace]!!.containsKey(knownApp))
            assertTrue(it[knownNamespace]!![knownApp]!!.contains("POST /done"))
        }
    }

    @Test
    fun `Test that evaluation of all rules do not throw exception`() {
        rulesSet.forEach { it.values.forEach { it.values.forEach { it.forEach { it.evaluateAsRule(Method.PURGE, "/not/present/path!!!!!") } } } }
    }

    @Test
    fun `Test that unparsable rule method throw exception`() {
        assertThrows(IllegalArgumentException::class.java) {
            "GE /path".evaluateAsRule(Method.GET, "/path")
        }
    }

    @Test
    fun `Test that different paths give expected result from evaluation as rule`() {
        assertTrue("POST /done".evaluateAsRule(Method.POST, "/done"))
        assertFalse("GET /done".evaluateAsRule(Method.POST, "/done"))
        assertFalse("GET /done".evaluateAsRule(Method.POST, "/don"))
        assertFalse("GET /done".evaluateAsRule(Method.POST, "/done/"))

        assertTrue("GET /path/.*/ending".evaluateAsRule(Method.GET, "/path/something/in/the/middle/ending"))
        assertTrue("GET /api/.*".evaluateAsRule(Method.GET, "/api/something/in/the/end"))
        assertFalse("GET /api/.*".evaluateAsRule(Method.GET, "/something/in/the/end"))
    }

    @Test
    fun `Test scope configuration translates correctly with findScope function`() {
        assertEquals("defaultaccess", listOf("POST /done").findScope())
        assertEquals("consumer-beregningsgrunnlag", listOf("POST /done scope:consumer-beregningsgrunnlag").findScope())
    }
}
