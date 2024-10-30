package no.nav.saas.proxy.whitelist

import com.google.gson.Gson
import mu.KotlinLogging
import no.nav.saas.proxy.Application
import no.nav.saas.proxy.Metrics
import no.nav.saas.proxy.TARGET_APP
import no.nav.saas.proxy.TARGET_NAMESPACE
import no.nav.saas.proxy.ingresses.Ingresses.ingressOf
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path

typealias RuleSet = Map<String, Map<String, List<String>>> // Map of namespaces, with map of apps, with list of patterns
typealias Rule = String

object Whitelist {
    private val log = KotlinLogging.logger { }

    fun parse(filePath: String): RuleSet =
        Gson().fromJson<RuleSet>(Application::class.java.getResource(filePath).readText(), Map::class.java)

    fun Rule.evaluateAsRule(method: Method, path: String): Boolean {
        val split = this.split(" ")
        val methodPart = Method.valueOf(split[0])
        val pathPart = split[1]
        return method == methodPart && Regex(pathPart).matches(path)
    }

    fun RuleSet.namespaceOfApp(app: String): String? {
        val namespaces = this.filter { it.value.keys.contains(app) }.map { it.key }
        if (namespaces.size > 1) throw IllegalStateException("App found in two namespaces in rules and no namespace header provided - cannot deduce ruleset")
        return this.filter { it.value.keys.contains(app) }.map { it.key }.firstOrNull()
    }

    fun RuleSet.rulesOf(app: String, namespace: String): List<Rule> {
        return this[namespace]?.let { it[app] } ?: listOf()
    }

    fun List<Rule>.findScope(): String =
        this.map {
            val split = it.split(" ")
            if (split.size > 2) {
                split[2].removePrefix("scope:")
            } else {
                ""
            }
        }.firstOrNull { it.isNotEmpty() } ?: "defaultaccess"

    val testRulesHandler = { req: Request ->
        req.headers
        val path = (req.path("rest") ?: "")
        Metrics.testApiCalls.labels(path).inc()
        log.info { "Test url called with path $path" }
        val method = req.method
        val targetApp = req.header(TARGET_APP)
        val targetNamespace = req.header(TARGET_NAMESPACE)
        if (targetApp == null) {
            Response(Status.BAD_REQUEST).body("Proxy: Missing target-app header")
        } else {
            val namespace = targetNamespace ?: Application.ruleSet.namespaceOfApp(targetApp) ?: ""
            val ingress = Application.ingressSet.ingressOf(targetApp, namespace)

            val rules = Application.ruleSet.rulesOf(targetApp, namespace)
            if (rules.isEmpty()) {
                Response(Status.NON_AUTHORITATIVE_INFORMATION).body("App not found in rules. Not approved")
            } else {
                var report = "Report:\n"
                report += if (ingress != null) "Targets ingress $ingress\n" else "Targets app in gcp\n"
                val approved = rules.filter {
                    report += "Evaluating $it on method ${req.method}, path /$path "
                    it.evaluateAsRule(method, "/$path").also { report += "$it\n" }
                }.isNotEmpty()
                report += if (approved) "Approved" else "Not approved"
                Response(Status.OK).body(report)
            }
        }
    }
}
