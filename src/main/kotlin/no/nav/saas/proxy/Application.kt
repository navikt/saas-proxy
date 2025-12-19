package no.nav.saas.proxy

import com.google.gson.Gson
import mu.KotlinLogging
import no.nav.saas.proxy.HttpClientResources.client
import no.nav.saas.proxy.ingresses.IngressSet
import no.nav.saas.proxy.ingresses.Ingresses
import no.nav.saas.proxy.ingresses.Ingresses.ingressOf
import no.nav.saas.proxy.token.TokenExchangeHandler
import no.nav.saas.proxy.token.TokenValidation
import no.nav.saas.proxy.token.Valkey
import no.nav.saas.proxy.whitelist.RuleSet
import no.nav.saas.proxy.whitelist.Whitelist
import no.nav.saas.proxy.whitelist.Whitelist.evaluateAsRule
import no.nav.saas.proxy.whitelist.Whitelist.findScope
import no.nav.saas.proxy.whitelist.Whitelist.namespaceOfApp
import no.nav.saas.proxy.whitelist.Whitelist.rulesOf
import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import java.io.File
import java.nio.ByteBuffer

const val TARGET_APP = "target-app"
const val TARGET_NAMESPACE = "target-namespace"
const val TARGET_ONLY_REDIRECT = "target-only-redirect"

object Application {
    private val log = KotlinLogging.logger { }

    const val USE_VALKEY = true

    val cluster = env(env_NAIS_CLUSTER_NAME)

    private val blockFromForwarding =
        listOf(TARGET_APP, TARGET_NAMESPACE, TARGET_ONLY_REDIRECT, "host", "authorization").map { it.lowercase() }

    // Hop-by-hop headers as defined by RFC 7230 section 6.1.
    // These headers are specific to a single transport-level connection and should not be forwarded by proxies.
    private val blockFromResponse =
        listOf(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "content-length",
            "upgrade",
        )

    val ruleSet: RuleSet = Whitelist.parse(env(config_WHITELIST_FILE))

    val ingressSet: IngressSet = Ingresses.parse(env(config_INGRESS_FILE))

    fun apiServer(port: Int): Http4kServer = api().asServer(Netty(port))

    fun api(): HttpHandler =
        routes(
            "/internal/isAlive" bind Method.GET to { Response(OK) },
            "/internal/isReady" bind Method.GET to isReadyHttpHandler,
            "/internal/metrics" bind Method.GET to Metrics.metricsHttpHandler,
            "/internal/test/{rest:.*}" bind Whitelist.testRulesHandler,
            "/internal/gui" bind Method.GET to static(ResourceLoader.Classpath("gui")),
            "/internal/lastseen" bind lastSeenHandler,
            "/{rest:.*}" bind redirectHttpHandler,
        )

    fun start() {
        // HttpClientResources.scheduleConnectionMetricsUpdater()

        apiServer(8080).start()
        File("/tmp/started").writeText("started")
    }

    private val isReadyHttpHandler: HttpHandler = {
        if (Valkey.isReady() && TokenValidation.isReady()) {
            Response(OK)
        } else {
            Response(Status.SERVICE_UNAVAILABLE)
        }
    }

    private val lastSeenHandler: HttpHandler = {
        val data = Valkey.fetchAllLastSeen() // returns Map<String, Map<String, Long>>
        Response(OK)
            .header("Content-Type", "application/json")
            .body(Gson().toJson(data))
    }

    private val redirectHttpHandler = { req: Request ->
        val millisAtStart = System.currentTimeMillis()
        val path = req.path("rest") ?: ""

        val targetApp = req.header(TARGET_APP)
        val targetNamespace = req.header(TARGET_NAMESPACE) // optional, but recommended
        val targetOnlyRedirect = req.header(TARGET_ONLY_REDIRECT) != null

        try {
            Metrics.apiCalls.labels(targetApp, Metrics.mask(path)).inc()
        } catch (e: Exception) {
            log.error { "Could not register api call metric " + e.message }
            File("/tmp/failRegisterApiCall").writeText(e.stackTraceToString())
        }

        if (targetOnlyRedirect) {
            if (TokenValidation.firstValidToken(req) != null) {
                val ingress = req.header(TARGET_ONLY_REDIRECT)
                val forwardHeaders =
                    req.headers
                        .filter {
                            !(blockFromForwarding.contains(it.first.lowercase()))
                        }.toList()
                val url = "$ingress${req.uri}"
                val redirect = Request(req.method, url).body(req.body).headers(forwardHeaders)
                val response = client(redirect)
                log.info { "Forwarded call to $url" }
//                try {
//                    File("/tmp/latestRedirect-${response.status.code}").writeText(
//                        "$currentDateTime\n\nREQUEST:\n" + req.toMessage() + "\n\nREDIRECT:\n" + redirect.toMessage() + "\n\nRESPONSE:\n" + response.toMessage()
//                    )
//                } catch (e: Exception) {
//                    log.error { "Failed to store forwarded call" }
//                }
                response.withoutBlockedHeaders()
            } else {
                Response(UNAUTHORIZED)
            }
        } else if (targetApp == null) {
            log.info { "Proxy: Bad request - missing targetApp header" }
            File("/tmp/missingheader").writeText(req.toMessage())
            Response(BAD_REQUEST).body("Proxy: Bad request - missing header")
        } else {
            val namespace = targetNamespace ?: ruleSet.namespaceOfApp(targetApp) ?: ""
            val ingress = ingressSet.ingressOf(targetApp, namespace)
            val approvedByRules =
                ruleSet
                    .rulesOf(targetApp, namespace)
                    .filter { it.evaluateAsRule(req.method, "/$path") }

            val token = TokenValidation.firstValidToken(req)

            if (approvedByRules.isEmpty()) {
                log.info { "Proxy: Bad request - not whitelisted path for app $targetApp, path $path" }
                File("/tmp/notwhitelisted-$targetApp").writeText(
                    "$currentDateTime\n\nREQUEST:\n" + req.toMessage(),
                )
                Response(BAD_REQUEST).body("Proxy: Bad request - $path is not whitelisted")
            } else if (token == null) {
                log.info { "Proxy: Not authorized - target app $targetApp" }
                File("/tmp/noauth-$targetApp").writeText(
                    "$currentDateTime\n\n" + req.toMessage(),
                )
                Metrics.noAuth.labels(targetApp).inc()
                Response(UNAUTHORIZED).body("Proxy: Not authorized")
            } else {

                val forwardHeaders =
                    req.headers
                        .filter {
                            !(blockFromForwarding.contains(it.first.lowercase()))
                        }.toList() +
                        listOf(
                            "Authorization" to "Bearer ${
                                TokenExchangeHandler.exchange(
                                    jwtIn = token,
                                    targetAlias = "${targetCluster(ingress)}.$namespace.$targetApp",
                                    scope = approvedByRules.findScope(),
                                ).encodedToken}",
                        )

                val host = ingress ?: "http://$targetApp.$namespace"
                val internUrl = "$host${req.uri}" // svc.cluster.local skipped due to same cluster
                val redirect = Request(req.method, internUrl).body(req.body).headers(forwardHeaders)

                try {
                    val millisBeforeRedirect = System.currentTimeMillis()
                    val response = client(redirect)
                    val millisAfterRedirect = System.currentTimeMillis()

                    val redirectCallTime = millisAfterRedirect - millisBeforeRedirect
                    val totalCallTime = millisAfterRedirect - millisAtStart
                    val handlingTokenTime = totalCallTime - redirectCallTime

                    log.info {
                        "Forwarded call (${response.status}) to ${req.method.name} $internUrl (target cluster ${targetCluster(
                            ingress,
                        )}) - call time $totalCallTime ms ($handlingTokenTime handling, $redirectCallTime redirect)"
                    }

                    if ((!response.status.successful && response.status.code != 404) || response.status.code == 201) {
                        File(
                            "/tmp/latest-$targetApp-${response.status.code}",
                        ).writeText("${currentDateTime}\nREDIRECT:\n${redirect.toMessage()}\n\nRESPONSE:\n${response.toMessage()}")
                    }

                    try {
                        val tokenType = "proxy:${if (TokenExchangeHandler.isOBOToken(token)) "obo" else "m2m"}"
                        Metrics.forwardedCallsInc(
                            targetApp = targetApp,
                            path = Metrics.mask(path),
                            ingress = ingress ?: "",
                            tokenType = tokenType,
                            status = response.status.code.toString(),
                            totalMs = totalCallTime,
                            handlingMs = handlingTokenTime,
                        )
                    } catch (e: Exception) {
                        log.error { "Could not register forwarded call metric " + e.message }
                    }

                    try {
                        if (USE_VALKEY) {
                            Valkey.updateAppLastSeen(targetApp, namespace)
                        }
                    } catch (e: Exception) {
                        log.error { "Could not store timestamp for app call " + e.message }
                    }

                    response.withoutBlockedHeaders()
                } catch (e: Exception) {
                    // To catch issues in the client(request) call
                    log.error { "Failed call to $internUrl (target cluster ${targetCluster(ingress)}))" }
                    File(
                        "/tmp/latest-$targetApp-EXCEPTION",
                    ).writeText("${currentDateTime}\nREDIRECT:\n${redirect.toMessage()}\n\nRESPONSE:\n${e.stackTraceToString()}")
                    if (redirect.method == Method.GET) {
                        // Retry on exceptions on GET // TODO refactor
                        try {
                            val response = client(redirect)
                            val tokenType = "proxy:${if (TokenExchangeHandler.isOBOToken(token)) "obo" else "m2m"}"
                            Metrics.forwardedCallsInc(
                                targetApp = targetApp,
                                path = Metrics.mask(path),
                                ingress = ingress ?: "",
                                tokenType = tokenType,
                                status = "retry-" + response.status.code.toString(),
                            )
                            response.withoutBlockedHeaders()
                        } catch (e: Exception) {
                            val tokenType = "proxy:${if (TokenExchangeHandler.isOBOToken(token)) "obo" else "m2m"}"
                            Metrics.forwardedCallsInc(
                                targetApp = targetApp,
                                path = Metrics.mask(path),
                                ingress = ingress ?: "",
                                tokenType = tokenType,
                                status = "retry-500",
                            )
                            Response(Status.INTERNAL_SERVER_ERROR).body(e.stackTraceToString())
                        }
                    } else {
                        try {
                            val tokenType = "proxy:${if (TokenExchangeHandler.isOBOToken(token)) "obo" else "m2m"}"
                            Metrics.forwardedCallsInc(
                                targetApp = targetApp,
                                path = Metrics.mask(path),
                                ingress = ingress ?: "",
                                tokenType = tokenType,
                                status = "500",
                            )
                        } catch (e: Exception) {
                            log.error { "Could not register forwarded call metric " + e.message }
                        }
                        Response(Status.INTERNAL_SERVER_ERROR).body(e.stackTraceToString())
                    }
                }
            }
        }
    }

    /**
     * targetCluster - resolves target cluster based on the cluster of the proxy (dev or prod)
     *                 with gcp replaced with fss if we are targeting an ingress
     */
    private fun targetCluster(specifiedIngress: String?) =
        specifiedIngress?.let {
            cluster.replace("gcp", "fss")
        } ?: cluster

    private fun Response.withoutBlockedHeaders(): Response {
        val filteredHeaders = this.headers.filter { (key, _) -> key.lowercase() !in blockFromResponse }

        val bodyBytes = this.body.stream.use { it.readBytes() }
        return Response(this.status)
            .headers(filteredHeaders)
            .body(Body(ByteBuffer.wrap(bodyBytes)))
    }
}
