import mu.KotlinLogging
import no.nav.saas.proxy.Application
import no.nav.saas.proxy.HOST
import no.nav.saas.proxy.Metrics
import no.nav.saas.proxy.TARGET_APP
import no.nav.saas.proxy.TARGET_CLIENT_ID
import no.nav.saas.proxy.audAsString
import no.nav.saas.proxy.evaluateAsRule
import no.nav.saas.proxy.ingressOf
import no.nav.saas.proxy.rulesOf
import no.nav.saas.proxy.targetCluster
import no.nav.saas.proxy.token.TokenExchangeHandler
import no.nav.saas.proxy.token.TokenValidation
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.path
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger { }
val testcall = { req: Request ->

    val path = "/api/v1/beregningsgrunnlag/transaksjoner?tom=2024-08-16&fom=2024-05-16"
    Metrics.apiCalls.labels(path).inc()

    val targetApp = "arena-api-q2"
    val targetClientId = null
    val targetNamespace = "teamarenanais"

    val namespace = targetNamespace
    val ingress = Application.ingressSet.ingressOf(targetApp, namespace)
    val approvedByRules = Application.ruleSet.rulesOf(targetApp, namespace)
        .filter { it.evaluateAsRule(req.method, path) }
        .isNotEmpty()

    val optionalToken = TokenValidation.firstValidToken(req, targetClientId ?: Application.clientIdProxy)

    if (!approvedByRules) {
        log.info { "Proxy: Bad request - not whitelisted" }
        Response(Status.BAD_REQUEST).body("Proxy: Bad request - not whitelisted path")
    } else if (!optionalToken.isPresent) {
        log.info { "Proxy: Not authorized" }
        File("/tmp/noauth-$targetApp").writeText(req.toMessage())
        Response(Status.UNAUTHORIZED).body("Proxy: Not authorized")
    } else {
        val blockFromForwarding = listOf(TARGET_APP, TARGET_CLIENT_ID, HOST)

        var exchangeToken = false
        try {
            exchangeToken = optionalToken.get().audAsString() == Application.clientIdProxy
        } catch (e: Exception) {
            log.error { "Failed aud lookup!" }
        }

        val forwardHeaders = if (exchangeToken) {
            req.headers.filter {
                !(blockFromForwarding.contains(it.first) || it.first.lowercase() == "authorization")
            }.toList() + listOf(
                "Authorization" to "Bearer ${
                TokenExchangeHandler.exchange(
                    optionalToken.get(),
                    "${targetCluster(ingress)}.$namespace.$targetApp"
                ).tokenAsString}"
            )
        } else {
            req.headers.filter {
                !blockFromForwarding.contains(it.first)
            }.toList()
        }

        val host = ingress ?: "http://$targetApp.$namespace"
        val internUrl = "$host${"/$path"}" // svc.cluster.local skipped due to same cluster
        val redirect = Request(req.method, internUrl).body(req.body).headers(forwardHeaders)

        val response = Application.client(redirect)

        log.info { "Forwarded call TEST (${response.status}) to $internUrl (token exchange $exchangeToken, target cluster ${targetCluster(ingress)})" }

        try {
            val tokenType = "${if (exchangeToken) "proxy" else "app"}:${if (TokenExchangeHandler.isOBOToken(optionalToken.get())) "obo" else "m2m"}"
            Metrics.forwardedCallsInc(targetApp = targetApp, path = path, ingress = ingress ?: "", tokenType = tokenType, status = response.status.code.toString())
        } catch (e: Exception) {
            log.error { "Could not register forwarded call metric" }
        }

        try {
            File("/tmp/latestForwarded-$targetApp-${response.status.code}").writeText(
                LocalDateTime.now().format(
                    DateTimeFormatter.ISO_DATE_TIME
                ) + "\n\nREQUEST:\n" + req.toMessage() + "\n\nREDIRECT:\n" + redirect.toMessage() + "\n\nRESPONSE:\n" + response.toMessage()
            )
        } catch (e: Exception) {
            File("/tmp/FAILEDStoreForwardedCall").writeText("$targetApp")
            log.error { "Failed to store forwarded call" }
        }
        response
    }
}
