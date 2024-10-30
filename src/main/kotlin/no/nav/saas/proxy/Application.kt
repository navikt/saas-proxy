package no.nav.saas.proxy

import mu.KotlinLogging
import no.nav.saas.proxy.HttpClientResources.client
import no.nav.saas.proxy.token.Redis
import no.nav.saas.proxy.token.TokenExchangeHandler
import no.nav.saas.proxy.token.TokenValidation
import no.nav.security.token.support.core.jwt.JwtToken
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.NON_AUTHORITATIVE_INFORMATION
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.routing.bind
import org.http4k.routing.path
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

const val TARGET_APP = "target-app"
const val TARGET_CLIENT_ID = "target-client-id"
const val TARGET_NAMESPACE = "target-namespace"
const val HOST = "host"

object Application {
    private val log = KotlinLogging.logger { }

    const val useRedis = true

    val clientIdProxy = System.getenv("AZURE_APP_CLIENT_ID")

    val ruleSet = Rules.parse(System.getenv(config_WHITELIST_FILE))

    val ingressSet = Ingresses.parse(System.getenv(config_INGRESS_FILE))

    fun apiServer(port: Int): Http4kServer = api().asServer(Netty(port))

    fun api(): HttpHandler = routes(
        "/internal/isAlive" bind Method.GET to { Response(OK) },
        "/internal/isReady" bind Method.GET to Redis.isReadyHttpHandler,
        "/internal/metrics" bind Method.GET to Metrics.metricsHttpHandler,
        "/internal/test/{rest:.*}" bind testWhitelistHandler,
        "/{rest:.*}" bind redirectHttpHandler
    )

    fun start() {
        log.info { "Starting" }

        HttpClientResources.scheduleConnectionMetricsUpdater()

        apiServer(8080).start()
        log.info { "Entering cache query loop" }
        if (useRedis) {
            Redis.cacheQueryLoop()
        }
    }

    val testWhitelistHandler = { req: Request ->
        req.headers
        val path = (req.path("rest") ?: "")
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
    }

    val redirectHttpHandler = { req: Request ->
        val millisAtStart = System.currentTimeMillis()
        val path = req.path("rest") ?: ""

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
                File("/tmp/notwhitelisted-$targetApp").writeText(
                    LocalDateTime.now().format(
                        DateTimeFormatter.ISO_DATE_TIME
                    ) + "\n\nREQUEST:\n" + req.toMessage()
                )
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
                        handlingMs = handlingTokenTime
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
