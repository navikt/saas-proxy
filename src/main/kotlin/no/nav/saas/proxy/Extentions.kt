package no.nav.saas.proxy

import no.nav.security.token.support.core.http.HttpRequest
import org.http4k.core.Method
import org.http4k.core.Request

fun String.evaluateAsRule(method: Method, path: String): Boolean {
    val split = this.split(" ")
    val methodPart = Method.valueOf(split[0])
    val pathPart = split[1]
    return method == methodPart && Regex(pathPart).matches(path)
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
