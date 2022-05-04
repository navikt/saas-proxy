
import no.nav.saas.proxy.Rules
import no.nav.saas.proxy.evaluateAsRule
import org.http4k.core.Method
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

const val WHITELIST_DEV = "/whitelist/dev.json"
const val WHITELIST_PROD = "/whitelist/prod.json"

public class WhitelistTest {
    val devRules = Rules.parse(WHITELIST_DEV)
    val prodRules = Rules.parse(WHITELIST_PROD)

    val rulesSet = setOf(devRules, prodRules)

    @Test
    fun test_that_parsing_dev_whitelist_contains_known_rule() {
        devRules.let {
            val knownTeam = "teamnks"
            val knownApp = "sf-brukernotifikasjon"
            assertTrue(it.containsKey(knownTeam))
            assertTrue(it[knownTeam]!!.containsKey(knownApp))
            assertTrue(it[knownTeam]!![knownApp]!!.contains("POST /done"))
        }
    }

    @Test
    fun test_that_parsing_prod_whitelist_contains_known_rule() {
        prodRules.let {
            val knownTeam = "teamnks"
            val knownApp = "sf-brukernotifikasjon"
            assertTrue(it.containsKey(knownTeam))
            assertTrue(it[knownTeam]!!.containsKey(knownApp))
            assertTrue(it[knownTeam]!![knownApp]!!.contains("POST /done"))
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
    fun test_that_that_each_app_entry_is_unique() {
        rulesSet.forEach {
            val listOfApps = it.values.flatMap { it.keys }
            assertEquals(listOfApps.size, listOfApps.toSet().size)
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
