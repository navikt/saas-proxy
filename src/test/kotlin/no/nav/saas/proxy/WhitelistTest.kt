
import java.lang.IllegalStateException
import no.nav.saas.proxy.Rules
import no.nav.saas.proxy.evaluateAsRule
import no.nav.saas.proxy.namespaceOfApp
import no.nav.saas.proxy.rulesOf
import org.http4k.core.Method
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

const val WHITELIST_DEV = "/whitelist/dev.json"
const val WHITELIST_PROD = "/whitelist/prod.json"

public class WhitelistTest {
    val devRuleSet = Rules.parse(WHITELIST_DEV)
    val prodRuleSet = Rules.parse(WHITELIST_PROD)

    val rulesSet = setOf(devRuleSet, prodRuleSet)

    @Test
    fun test_that_parsing_dev_whitelist_contains_known_rule() {
        devRuleSet.let {
            val knownNamespace = "teamnks"
            val knownApp = "sf-brukernotifikasjon"
            assertTrue(it.containsKey(knownNamespace))
            assertTrue(it[knownNamespace]!!.containsKey(knownApp))
            assertTrue(it[knownNamespace]!![knownApp]!!.contains("POST /done"))
        }
    }

    @Test
    fun test_lookup_function_for_namespace_returns_existing_namespace_or_null() {
        val knownNamespace = "teamnks"
        val knownApp = "sf-brukernotifikasjon"
        assertTrue(devRuleSet.namespaceOfApp(knownApp) == knownNamespace)
        assertTrue(devRuleSet.namespaceOfApp("NON_EXISTING") == null)
    }

    @Test
    fun test_lookup_function_for_rules_returns_list_of_rules_or_empty_list() {
        val knownNamespace = "teamnks"
        val knownApp = "sf-brukernotifikasjon"
        assertTrue(devRuleSet.rulesOf(knownApp, knownNamespace).isNotEmpty())
        assertTrue(devRuleSet.rulesOf("NON_EXISTING", knownNamespace).isEmpty())
        assertTrue(devRuleSet.rulesOf(knownApp, "NON_EXISTING").isEmpty())
    }

    @Test
    fun test_lookup_function_for_namespace_throws_exception_if_app_is_found_in_two_namespaces_in_ruleset() {
        val knownApp = "sf-brukernotifikasjon"
        val devRuleSetWithTwinAppInAnotherNamespace = devRuleSet + mapOf("Other namespace" to mapOf(knownApp to listOf("Other Rule")))
        assertThrows(IllegalStateException::class.java) {
            devRuleSetWithTwinAppInAnotherNamespace.namespaceOfApp(knownApp)
        }
    }

    @Test
    fun test_that_parsing_prod_whitelist_contains_known_rule() {
        prodRuleSet.let {
            val knownNamespace = "teamnks"
            val knownApp = "sf-brukernotifikasjon"
            assertTrue(it.containsKey(knownNamespace))
            assertTrue(it[knownNamespace]!!.containsKey(knownApp))
            assertTrue(it[knownNamespace]!![knownApp]!!.contains("POST /done"))
        }
    }

    @Test
    fun test_that_evaluation_of_all_rules_do_not_throw_exception() {
        rulesSet.forEach { it.values.forEach { it.values.forEach { it.forEach { it.evaluateAsRule(Method.PURGE, "/not/present/path!!!!!") } } } }
    }

    @Test
    fun test_that_unparsable_rule_method_throw_exception() {
        assertThrows(IllegalArgumentException::class.java) {
            "GE /path".evaluateAsRule(Method.GET, "/path")
        }
    }

    @Test
    fun test_that_different_paths_give_expected_result_from_evaluation_as_rule() {
        assertTrue("POST /done".evaluateAsRule(Method.POST, "/done"))
        assertFalse("GET /done".evaluateAsRule(Method.POST, "/done"))
        assertFalse("GET /done".evaluateAsRule(Method.POST, "/don"))
        assertFalse("GET /done".evaluateAsRule(Method.POST, "/done/"))

        assertTrue("GET /path/.*/ending".evaluateAsRule(Method.GET, "/path/something/in/the/middle/ending"))
        assertTrue("GET /api/.*".evaluateAsRule(Method.GET, "/api/something/in/the/end"))
        assertFalse("GET /api/.*".evaluateAsRule(Method.GET, "/something/in/the/end"))
    }
}
