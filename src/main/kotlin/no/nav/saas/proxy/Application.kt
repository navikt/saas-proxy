package no.nav.saas.proxy

import io.prometheus.client.exporter.common.TextFormat
import mu.KotlinLogging
import no.nav.saas.proxy.token.TokenValidation
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClients
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
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
import java.io.StringWriter

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

object Application {
    private val log = KotlinLogging.logger { }

    val ruleSet = Rules.parse(System.getenv(env_WHITELIST_FILE))

    val httpClient = HttpClients.custom()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectTimeout(60000)
                .setSocketTimeout(60000)
                .setConnectionRequestTimeout(60000)
                .setRedirectsEnabled(false)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                .build()
        ).setMaxConnPerRoute(20).setMaxConnTotal(30).build()

    val client = ApacheClient(httpClient)

    fun start() {
        log.info { "Starting" }
        apiServer(NAIS_DEFAULT_PORT).start()
        log.info { "Finished!" }
    }

    fun apiServer(port: Int): Http4kServer = api().asServer(Netty(port))

    fun api(): HttpHandler = routes(
        NAIS_ISALIVE bind Method.GET to { Response(Status.OK) },
        NAIS_ISREADY bind Method.GET to { Response(Status.OK) },
        NAIS_METRICS bind Method.GET to {
            runCatching {
                StringWriter().let { str ->
                    TextFormat.write004(str, Metrics.cRegistry.metricFamilySamples())
                    str
                }.toString()
            }
                .onFailure {
                    log.error { "/prometheus failed writing metrics - ${it.localizedMessage}" }
                }
                .getOrDefault("").let {
                    if (it.isNotEmpty()) Response(Status.OK).body(it) else Response(Status.NO_CONTENT)
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

                val rules = Application.ruleSet.rulesOf(targetApp, namespace)
                if (rules.isEmpty()) {
                    Response(NON_AUTHORITATIVE_INFORMATION).body("App not found in rules. Not approved")
                } else {
                    var report = "Report:\n"
                    val approved = rules.filter {
                        report += "Evaluating $it on method ${req.method}, path /$path "
                        it.evaluateAsRule(method, "/$path").also { report += "$it\n" }
                    }.isNotEmpty()
                    report += if (approved) "Approved" else "Not approved"
                    Response(OK).body(report)
                }
            }
        },
        API_URI bind { req: Request ->
            val path = req.path(API_URI_VAR) ?: ""
            Metrics.apiCalls.labels(path).inc()

            val targetApp = req.header(TARGET_APP)
            val targetClientId = req.header(TARGET_CLIENT_ID)
            val targetNamespace = req.header(TARGET_NAMESPACE) // optional

            if (targetApp == null || targetClientId == null) {
                log.info { "Proxy: Bad request - missing header" }
                File("/tmp/missingheader").writeText(req.toMessage())

                Response(BAD_REQUEST).body("Proxy: Bad request - missing header")
            } else {
                val namespace = targetNamespace ?: ruleSet.namespaceOfApp(targetApp) ?: ""
                val approvedByRules = Application.ruleSet.rulesOf(targetApp, namespace)
                    .filter { it.evaluateAsRule(req.method, "/$path") }
                    .isNotEmpty()

                if (!approvedByRules) {
                    log.info { "Proxy: Bad request - not whitelisted" }
                    Response(BAD_REQUEST).body("Proxy: Bad request")
                } else if (!TokenValidation.containsValidToken(req, targetClientId)) {
                    log.info { "Proxy: Not authorized" }
                    Response(UNAUTHORIZED).body("Proxy: Not authorized")
                } else {
                    val blockFromForwarding = listOf(TARGET_APP, TARGET_CLIENT_ID, HOST)
                    val forwardHeaders =
                        req.headers.filter {
                            !blockFromForwarding.contains(it.first)
                        }.toList()
                    val internUrl = "http://$targetApp.$namespace${req.uri}" // svc.cluster.local skipped due to same cluster
                    val redirect = Request(req.method, internUrl).body(req.body).headers(forwardHeaders)
                    log.info { "Forwarded call to $internUrl" }

                    client(redirect)
                }
            }
        }
    )
}
