package no.nav.saas.proxy

import io.prometheus.client.exporter.common.TextFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.saas.proxy.token.Redis
import no.nav.saas.proxy.token.TokenExchangeHandler
import no.nav.saas.proxy.token.TokenValidation
import no.nav.security.token.support.core.jwt.JwtToken
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.pool.PoolStats
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.NON_AUTHORITATIVE_INFORMATION
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SERVICE_UNAVAILABLE
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import java.io.File
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

const val NAIS_DEFAULT_PORT = 8080
const val NAIS_ISALIVE = "/internal/isAlive"
const val NAIS_ISREADY = "/internal/isReady"
const val NAIS_METRICS = "/internal/metrics"

const val API_URI_VAR = "rest"
const val API_INTERNAL_TEST_URI = "/internal/test/{$API_URI_VAR:.*}"
const val API_URI = "/{$API_URI_VAR:.*}"

const val TARGET_APP = "target-app"
const val TARGET_CLIENT_ID = "target-client-id"
const val TARGET_NAMESPACE = "target-namespace"
const val HOST = "host"

const val env_WHITELIST_FILE = "WHITELIST_FILE"
const val env_INGRESS_FILE = "INGRESS_FILE"

object Application {
    private val log = KotlinLogging.logger { }

    val clientIdProxy = System.getenv("AZURE_APP_CLIENT_ID")

    val ruleSet = Rules.parse(System.getenv(env_WHITELIST_FILE))

    val ingressSet = Ingresses.parse(System.getenv(env_INGRESS_FILE))

    val connectionManager = PoolingHttpClientConnectionManager().apply {
        maxTotal = 30 // Set max total connections
        defaultMaxPerRoute = 20 // Set max connections per route
    }

    val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    val httpClient = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectTimeout(60000)
                .setSocketTimeout(60000)
                .setConnectionRequestTimeout(60000)
                .setRedirectsEnabled(false)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                .build()
        ).build()

    val client = ApacheClient(httpClient)

    fun start() {
        log.info { "Starting" }

        executor.scheduleAtFixedRate(
            {
                val stats: PoolStats = connectionManager.totalStats
                Metrics.activeConnections.set(stats.leased.toDouble())
                Metrics.idleConnections.set(stats.available.toDouble())
                Metrics.maxConnections.set(connectionManager.maxTotal.toDouble())
                Metrics.pendingConnections.set(stats.pending.toDouble())
            },
            0,
            10,
            TimeUnit.SECONDS
        )

        apiServer(NAIS_DEFAULT_PORT).start()
        log.info { "Entering cache query loop" }
        if (Redis.useMe) {
            cacheQueryLoop()
        }
    }

    tailrec fun cacheQueryLoop() {
        runBlocking { delay(60000) } // 1 min
        try {
            Metrics.cacheSize.set(Redis.dbSize().toDouble())
        } catch (e: Exception) {
            log.warn { "Failed to query Redis dbSize" }
        }
        runBlocking { delay(840000) } // 14 min
        cacheQueryLoop()
    }

    fun apiServer(port: Int): Http4kServer = api().asServer(Netty(port))

    var initialCheckPassed = false

    fun api(): HttpHandler = routes(
        NAIS_ISALIVE bind Method.GET to { Response(OK) },
        NAIS_ISREADY bind Method.GET to {
            if (initialCheckPassed) {
                Response(OK)
            } else {
                var response = 0L
                val queryTime = measureTimeMillis {
                    response = Redis.dbSize()
                }
                log.info { "Initial check query time $queryTime ms (got count $response)" }
                if (queryTime < 100) {
                    initialCheckPassed = true
                }
                Response(SERVICE_UNAVAILABLE)
            }
        },
        NAIS_METRICS bind Method.GET to {
            runCatching {
                StringWriter().let { str ->
                    TextFormat.write004(str, Metrics.cRegistry.metricFamilySamples())
                    str
                }.toString()
            }
                .onFailure {
                    log.error { "/prometheus failed writing metrics  - ${it.localizedMessage}" }
                }
                .getOrDefault("").let {
                    if (it.isNotEmpty()) Response(OK).body(it) else Response(Status.NO_CONTENT)
                }
        },
        API_INTERNAL_TEST_URI bind { req: Request ->
            req.headers
            val path = (req.path(API_URI_VAR) ?: "")
            Metrics.testApiCalls.labels(path).inc()
            log.info { "Test url called with path $path" }
            val method = req.method
            val targetApp = req.header(TARGET_APP)
            val targetNamespace = req.header(TARGET_NAMESPACE)
            if (targetApp == null) {
                Response(BAD_REQUEST).body("Proxy: Missing target-app header")
            } else {
                val namespace = targetNamespace ?: ruleSet.namespaceOfApp(targetApp) ?: ""
                val ingress = ingressSet.ingressOf(targetApp, namespace)

                val rules = ruleSet.rulesOf(targetApp, namespace)
                if (rules.isEmpty()) {
                    Response(NON_AUTHORITATIVE_INFORMATION).body("App not found in rules. Not approved")
                } else {
                    var report = "Report:\n"
                    report += if (ingress != null) "Targets ingress $ingress\n" else "Targets app in gcp\n"
                    val approved = rules.filter {
                        report += "Evaluating $it on method ${req.method}, path /$path "
                        it.evaluateAsRule(method, "/$path").also { report += "$it\n" }
                    }.isNotEmpty()
                    report += if (approved) "Approved" else "Not approved"
                    Response(OK).body(report)
                }
            }
        },
        API_URI bind redirect
    )

    val redirect = { req: Request ->
        val millisAtStart = System.currentTimeMillis()
        val path = req.path(API_URI_VAR) ?: ""

        val targetApp = req.header(TARGET_APP)
        val targetClientId = req.header(TARGET_CLIENT_ID)
        val targetNamespace = req.header(TARGET_NAMESPACE) // optional

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

            val optionalToken = TokenValidation.firstValidToken(req, targetClientId ?: clientIdProxy)

            if (approvedByRules.isEmpty()) {
                log.info { "Proxy: Bad request - not whitelisted" }
                Response(BAD_REQUEST).body("Proxy: Bad request - $path is not whitelisted")
            } else if (!optionalToken.isPresent) {
                log.info { "Proxy: Not authorized" }
                File("/tmp/noauth-$targetApp").writeText(
                    LocalDateTime.now().format(
                        DateTimeFormatter.ISO_DATE_TIME
                    ) + "\n\n" + req.toMessage()
                )
                Metrics.noAuth.labels(targetApp).inc()
                Response(UNAUTHORIZED).body("Proxy: Not authorized")
            } else {
                val blockFromForwarding = listOf(TARGET_APP, TARGET_CLIENT_ID, HOST)

                val exchangeToken = optionalToken.get().audAsString() == clientIdProxy

                val forwardHeaders = if (exchangeToken) {
                    req.headers.filter {
                        !(blockFromForwarding.contains(it.first) || it.first.lowercase() == "authorization")
                    }.toList() + listOf(
                        "Authorization" to "Bearer ${TokenExchangeHandler.exchange(
                            optionalToken.get(),
                            "${targetCluster(ingress)}.$namespace.$targetApp",
                            approvedByRules.findScope()
                        ).tokenAsString}"
                    )
                } else {
                    req.headers.filter {
                        !blockFromForwarding.contains(it.first)
                    }.toList()
                }

                val host = ingress ?: "http://$targetApp.$namespace"
                val internUrl = "$host${req.uri}" // svc.cluster.local skipped due to same cluster
                val redirect = Request(req.method, internUrl).body(req.body).headers(forwardHeaders)

                val millisBeforeRedirect = System.currentTimeMillis()
                val response = client(redirect)
                val millisAfterRedirect = System.currentTimeMillis()

                val redirectCallTime = millisAfterRedirect - millisBeforeRedirect
                val totalCallTime = millisAfterRedirect - millisAtStart
                val handlingTokenTime = totalCallTime - redirectCallTime

                log.info { "Forwarded call (${response.status}) to $internUrl (token exchange $exchangeToken, target cluster ${targetCluster(ingress)}) - call time $totalCallTime ms ($handlingTokenTime handling, $redirectCallTime redirect)" }

                try {
                    val tokenType = "${if (exchangeToken) "proxy" else "app"}:${if (TokenExchangeHandler.isOBOToken(optionalToken.get())) "obo" else "m2m"}"
                    Metrics.forwardedCallsInc(
                        targetApp = targetApp,
                        path = Metrics.mask(path),
                        ingress = ingress ?: "",
                        tokenType = tokenType,
                        status = response.status.code.toString(),
                        totalMs = totalCallTime,
                        handlingMs = handlingTokenTime,
                        redirectMs = redirectCallTime
                    )
                } catch (e: Exception) {
                    log.error { "Could not register forwarded call metric" }
                }

                try {
                    File("/tmp/latestForwarded-$targetApp-${(if (ingress == null) "service" else "ingress")}-${if (TokenExchangeHandler.isOBOToken(optionalToken.get())) "obo" else "m2m"}-${response.status.code}").writeText(
                        LocalDateTime.now().format(
                            DateTimeFormatter.ISO_DATE_TIME
                        ) + "\n\nREQUEST:\n" + req.toMessage() + "\n\nREDIRECT:\n" + redirect.toMessage() + "\n\nRESPONSE:\n" + response.toMessage()
                    )
                } catch (e: Exception) {
                    File("/tmp/FailedStoreForwardedCall").writeText("$targetApp")
                    log.error { "Failed to store forwarded call" }
                }
                response
            }
        }
    }
}

fun JwtToken.audAsString() = this.jwtTokenClaims.get("aud").toString().let { it.substring(1, it.length - 1) }

fun targetCluster(specifiedIngress: String?): String {
    val currentCluster = System.getenv("NAIS_CLUSTER_NAME")
    return specifiedIngress?.let {
        currentCluster.replace("gcp", "fss")
    } ?: currentCluster
}
