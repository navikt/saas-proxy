@file:Suppress("ktlint:standard:property-naming")

package no.nav.saas.proxy

import mu.KotlinLogging
import mu.withLoggingContext
import no.nav.saas.proxy.HttpClientResources.client
import no.nav.saas.proxy.HttpClientResources.clientRetry
import no.nav.saas.proxy.gui.Gui
import no.nav.saas.proxy.ingresses.IngressSet
import no.nav.saas.proxy.ingresses.Ingresses
import no.nav.saas.proxy.ingresses.Ingresses.ingressOf
import no.nav.saas.proxy.teamlogs.LoggingLookup
import no.nav.saas.proxy.teamlogs.TeamLogging
import no.nav.saas.proxy.teamlogs.toLookup
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
import org.slf4j.MarkerFactory
import java.io.File
import java.nio.ByteBuffer
import java.time.Instant

const val TARGET_APP = "target-app"
const val TARGET_NAMESPACE = "target-namespace"
const val TARGET_ONLY_REDIRECT = "target-only-redirect"

object Application {
    private val log = KotlinLogging.logger { }

    const val USE_VALKEY = true

    val cluster = env(env_NAIS_CLUSTER_NAME)

    private val TEAM_LOGS = MarkerFactory.getMarker("TEAM_LOGS")

    val startedAt: Instant = Instant.now()

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

    val teamLogsLookup: LoggingLookup = TeamLogging.parse(env(config_TEAMLOGGING_FILE)).toLookup()

    fun apiServer(port: Int): Http4kServer = api().asServer(Netty(port))

    fun api(): HttpHandler =
        routes(
            "/internal/isAlive" bind Method.GET to { Response(OK) },
            "/internal/isReady" bind Method.GET to isReadyHttpHandler,
            "/internal/metrics" bind Method.GET to Metrics.metricsHttpHandler,
            "/internal/test/{rest:.*}" bind Whitelist.testRulesHandler,
            "/internal/gui" bind Method.GET to static(ResourceLoader.Classpath("gui")),
            "/internal/lastseen" bind Gui.lastSeenHandler,
            "/internal/startedAt" bind Gui.startedAtHandler,
            "/internal/whoAmI" bind Method.GET to { Response(OK).body(env(env_AZURE_APP_CLIENT_ID)) },
            "/{rest:.*}" bind redirectHttpHandler,
        )

    fun start() {
        // HttpClientResources.scheduleConnectionMetricsUpdater()
        apiServer(8080).start()
        File("/tmp/started").writeText("started2")
    }

    private val isReadyHttpHandler: HttpHandler = {
        if (Valkey.isReady() && TokenValidation.isReady()) {
            Response(OK)
        } else {
            Response(Status.SERVICE_UNAVAILABLE)
        }
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
                log.info { "Forwarded call to $url via only redirect" }
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
                        .filter { !(blockFromForwarding.contains(it.first.lowercase())) }
                        .toList() +
                        listOf(
                            // "Connection" to "close", //To test if stale connections in connection pools is an issue
                            "Authorization" to "Bearer ${
                                TokenExchangeHandler.exchange(
                                    jwtIn = token,
                                    targetAlias = "${targetCluster(ingress)}.$namespace.$targetApp",
                                    scope = approvedByRules.findScope(),
                                ).encodedToken}",
                        )

                val host = ingress ?: "http://$targetApp.$namespace"
                val targetUrl = "$host${req.uri}" // svc.cluster.local skipped due to same cluster
                val bufferedReq = req.bodyString().let { req.body(it) } // To be safe if streaming body
                val redirect = Request(req.method, targetUrl).body(bufferedReq.body).headers(forwardHeaders)
                val tokenType = if (TokenExchangeHandler.isOBOToken(token)) "obo" else "m2m"

                try {
                    val millisBeforeRedirect = System.currentTimeMillis()
                    val clientToUse = if (redirect.method == Method.GET) clientRetry else client
                    val response = clientToUse(redirect)
                    val millisAfterRedirect = System.currentTimeMillis()

                    val redirectCallTime = millisAfterRedirect - millisBeforeRedirect
                    val totalCallTime = millisAfterRedirect - millisAtStart
                    val handlingTokenTime = totalCallTime - redirectCallTime

                    val appKey = "$namespace.$targetApp"
                    val teamLogConfig = teamLogsLookup[appKey]
                    val gcpProject = teamLogConfig?.gcpProject

                    val logMessage =
                        "Forwarded call (${response.status}) to ${req.method.name} $targetUrl " +
                            "(with $tokenType-token) " +
                            "target cluster ${targetCluster(ingress)}) " +
                            "- call time $totalCallTime ms ($handlingTokenTime handling, $redirectCallTime redirect)"

                    withLoggingContext(
                        "statusCode" to response.status.code.toString(),
                        "method" to req.method.name,
                        "targetCluster" to targetCluster(ingress),
                        "targetApp" to targetApp,
                        "targetNamespace" to namespace,
                        "targetUrl" to targetUrl,
                        "totalCallTime" to "$totalCallTime",
                        "handlingTokenTime" to "$handlingTokenTime",
                        "tokenType" to tokenType,
                        "teamLogsGcpProject" to (gcpProject ?: ""),
                    ) {
                        log.info(logMessage)

                        // if ((!response.status.successful && response.status.code != 404) || response.status.code == 201) {
                        File(
                            "/tmp/latest-$targetApp-${response.status.code}",
                        ).writeText("${currentDateTime}\nREDIRECT:\n${redirect.toMessage()}\n\nRESPONSE:\n${response.toMessage()}")
                        // }

                        try {
                            Metrics.forwardedCallsInc(
                                targetApp = targetApp,
                                targetNamespace = namespace,
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
                        val safeResponse = response.withoutBlockedHeaders()
                        if (teamLogConfig != null) {
                            val teamContext =
                                mutableMapOf(
                                    "google_cloud_project" to teamLogConfig.gcpProject,
                                )
                            if (teamLogConfig.requestBody) {
                                teamContext["requestBody"] = redirect.bodyString()
                            }
                            if (teamLogConfig.responseBody) {
                                teamContext["responseBody"] = safeResponse.bodyString()
                            }
                            teamLogConfig.headers.forEach { header ->
                                redirect.header(header)?.let {
                                    teamContext["request-header-$header"] = it
                                }
                                safeResponse.header(header)?.let {
                                    teamContext["response-header-$header"] = it
                                }
                            }
                            withLoggingContext(teamContext) {
                                log.info(TEAM_LOGS, logMessage)
                            }
                        }
                        safeResponse
                    }
                } catch (e: Exception) {
                    // To catch issues in the client(request) call and retry once on GET
                    withLoggingContext(
                        "statusCode" to "EXCEPTION",
                        "method" to req.method.name,
                        "targetCluster" to targetCluster(ingress),
                        "targetApp" to targetApp,
                        "targetNamespace" to namespace,
                        "targetUrl" to targetUrl,
                        "tokenType" to tokenType,
                    ) {
                        log.error {
                            "Failed call to $targetUrl (target cluster ${targetCluster(ingress)}))" +
                                (if (redirect.method == Method.GET) " - will retry once" else "") + " ${e.message}"
                        }
                    }
                    File("/tmp/latest-$targetApp-EXCEPTION")
                        .writeText("${currentDateTime}\nREDIRECT:\n${redirect.toMessage()}\n\nRESPONSE:\n${e.stackTraceToString()}")

                    val (response, statusCodeForMetrics) =
                        if (redirect.method == Method.GET) {
                            try {
                                val retryResponse = client(redirect)
                                retryResponse.withoutBlockedHeaders() to "retry-${retryResponse.status.code}"
                            } catch (retryException: Exception) {
                                Response(Status.INTERNAL_SERVER_ERROR).body(retryException.stackTraceToString()) to "retry-500"
                            }
                        } else {
                            Response(Status.INTERNAL_SERVER_ERROR).body(e.stackTraceToString()) to "500"
                        }

                    Metrics.forwardedCallsInc(
                        targetApp = targetApp,
                        targetNamespace = namespace,
                        path = Metrics.mask(path),
                        ingress = ingress ?: "",
                        tokenType = tokenType,
                        status = statusCodeForMetrics,
                    )
                    response
                }
            }
        }
    }

    private fun Response.withoutBlockedHeaders(): Response {
        val filteredHeaders = this.headers.filter { (key, _) -> key.lowercase() !in blockFromResponse }

        val bodyBytes = this.body.stream.use { it.readBytes() }
        return Response(this.status)
            .headers(filteredHeaders)
            .body(Body(ByteBuffer.wrap(bodyBytes)))
    }
}
