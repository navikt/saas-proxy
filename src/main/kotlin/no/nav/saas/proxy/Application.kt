package no.nav.saas.proxy

import mu.KotlinLogging
import no.nav.saas.proxy.HttpClientResources.client
import no.nav.saas.proxy.ingresses.IngressSet
import no.nav.saas.proxy.ingresses.Ingresses
import no.nav.saas.proxy.ingresses.Ingresses.ingressOf
import no.nav.saas.proxy.token.Redis
import no.nav.saas.proxy.token.TokenExchangeHandler
import no.nav.saas.proxy.token.TokenValidation
import no.nav.saas.proxy.whitelist.RuleSet
import no.nav.saas.proxy.whitelist.Whitelist
import no.nav.saas.proxy.whitelist.Whitelist.evaluateAsRule
import no.nav.saas.proxy.whitelist.Whitelist.findScope
import no.nav.saas.proxy.whitelist.Whitelist.namespaceOfApp
import no.nav.saas.proxy.whitelist.Whitelist.rulesOf
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import java.io.File

const val TARGET_APP = "target-app"
const val TARGET_NAMESPACE = "target-namespace"

object Application {
    private val log = KotlinLogging.logger { }

    const val useRedis = true

    private val blockFromForwarding =
        listOf(TARGET_APP, TARGET_NAMESPACE, "host", "authorization").map { it.lowercase() }

    val clientIdProxy = env("AZURE_APP_CLIENT_ID")

    val ruleSet: RuleSet = Whitelist.parse(env(config_WHITELIST_FILE))

    val ingressSet: IngressSet = Ingresses.parse(env(config_INGRESS_FILE))

    fun apiServer(port: Int): Http4kServer = api().asServer(Netty(port))

    fun api(): HttpHandler = routes(
        "/internal/isAlive" bind Method.GET to { Response(OK) },
        "/internal/isReady" bind Method.GET to Redis.isReadyHttpHandler,
        "/internal/metrics" bind Method.GET to Metrics.metricsHttpHandler,
        "/internal/test/{rest:.*}" bind Whitelist.testRulesHandler,
        "/{rest:.*}" bind redirectHttpHandler
    )

    fun start() {
        HttpClientResources.scheduleConnectionMetricsUpdater()

        apiServer(8080).start()

        if (useRedis) {
            log.info { "Entering cache query loop" }
            Redis.cacheQueryLoop()
        }
    }

    private val redirectHttpHandler = { req: Request ->
        val millisAtStart = System.currentTimeMillis()
        val path = req.path("rest") ?: ""

        val targetApp = req.header(TARGET_APP)
        val targetNamespace = req.header(TARGET_NAMESPACE) // optional, but recommended

        Metrics.apiCalls.labels(targetApp, Metrics.mask(path)).inc()

        if (targetApp == null) {
            log.info { "Proxy: Bad request - missing targetApp header" }
            File("/tmp/missingheader").writeText(req.toMessage())
            Response(BAD_REQUEST).body("Proxy: Bad request - missing header")
        } else {
            val namespace = targetNamespace ?: ruleSet.namespaceOfApp(targetApp) ?: ""
            val ingress = ingressSet.ingressOf(targetApp, namespace)
            val approvedByRules = ruleSet.rulesOf(targetApp, namespace)
                .filter { it.evaluateAsRule(req.method, "/$path") }

            val optionalToken = TokenValidation.firstValidToken(req, clientIdProxy)

            if (approvedByRules.isEmpty()) {
                log.info { "Proxy: Bad request - not whitelisted" }
                File("/tmp/notwhitelisted-$targetApp").writeText(
                    "$currentDateTime\n\nREQUEST:\n" + req.toMessage()
                )
                Response(BAD_REQUEST).body("Proxy: Bad request - $path is not whitelisted")
            } else if (!optionalToken.isPresent) {
                log.info { "Proxy: Not authorized" }
                File("/tmp/noauth-$targetApp").writeText(
                    "$currentDateTime\n\n" + req.toMessage()
                )
                Metrics.noAuth.labels(targetApp).inc()
                Response(UNAUTHORIZED).body("Proxy: Not authorized")
            } else {

                val forwardHeaders =
                    req.headers.filter {
                        !(blockFromForwarding.contains(it.first.lowercase()))
                    }.toList() + listOf(
                        "Authorization" to "Bearer ${
                        TokenExchangeHandler.exchange(
                            jwtIn = optionalToken.get(),
                            targetAlias = "${targetCluster(ingress)}.$namespace.$targetApp",
                            scope = approvedByRules.findScope()
                        ).tokenAsString}"
                    )

                val host = ingress ?: "http://$targetApp.$namespace"
                val internUrl = "$host${req.uri}" // svc.cluster.local skipped due to same cluster
                val redirect = Request(req.method, internUrl).body(req.body).headers(forwardHeaders)

                val millisBeforeRedirect = System.currentTimeMillis()
                val response = client(redirect)
                val millisAfterRedirect = System.currentTimeMillis()

                val redirectCallTime = millisAfterRedirect - millisBeforeRedirect
                val totalCallTime = millisAfterRedirect - millisAtStart
                val handlingTokenTime = totalCallTime - redirectCallTime

                log.info { "Forwarded call (${response.status}) to $internUrl (target cluster ${targetCluster(ingress)}) - call time $totalCallTime ms ($handlingTokenTime handling, $redirectCallTime redirect)" }

                try {
                    val tokenType = "proxy:${if (TokenExchangeHandler.isOBOToken(optionalToken.get())) "obo" else "m2m"}"
                    Metrics.forwardedCallsInc(
                        targetApp = targetApp, path = Metrics.mask(path), ingress = ingress ?: "", tokenType = tokenType,
                        status = response.status.code.toString(), totalMs = totalCallTime, handlingMs = handlingTokenTime
                    )
                } catch (e: Exception) {
                    log.error { "Could not register forwarded call metric" }
                }

                try {
                    File("/tmp/latestForwarded-$targetApp-${(if (ingress == null) "service" else "ingress")}-${if (TokenExchangeHandler.isOBOToken(optionalToken.get())) "obo" else "m2m"}-${response.status.code}").writeText(
                        "$currentDateTime\n\nREQUEST:\n" + req.toMessage() + "\n\nREDIRECT:\n" + redirect.toMessage() + "\n\nRESPONSE:\n" + response.toMessage()
                    )
                } catch (e: Exception) {
                    log.error { "Failed to store forwarded call" }
                }
                response
            }
        }
    }

    private fun targetCluster(specifiedIngress: String?): String {
        val currentCluster = env("NAIS_CLUSTER_NAME")
        return specifiedIngress?.let {
            currentCluster.replace("gcp", "fss")
        } ?: currentCluster
    }
}
