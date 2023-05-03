package no.nav.saas.proxy

import no.nav.security.token.support.core.http.HttpRequest
import org.http4k.core.Method
import org.http4k.core.Request

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
    return this[namespace]?.let { it[app]?.let { it } } ?: listOf()
}

fun Request.toNavRequest(): HttpRequest {
    val req = this
    return object : HttpRequest {
        override fun getHeader(headerName: String): String {
            return req.header(headerName) ?: ""
        }
        override fun getCookies(): Array<HttpRequest.NameValue> {
            return arrayOf()
        }
    }
}
