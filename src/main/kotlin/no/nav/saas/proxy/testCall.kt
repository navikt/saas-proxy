import mu.KotlinLogging
import no.nav.saas.proxy.Application
import no.nav.saas.proxy.Metrics
import no.nav.saas.proxy.evaluateAsRule
import no.nav.saas.proxy.ingressOf
import no.nav.saas.proxy.rulesOf
import no.nav.saas.proxy.token.TokenValidation
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.path

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
    // ...

    Response(OK)
}
