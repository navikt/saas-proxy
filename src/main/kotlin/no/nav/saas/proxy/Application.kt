package no.nav.saas.proxy

import io.prometheus.client.exporter.common.TextFormat
import java.io.File
import java.io.StringWriter
import mu.KotlinLogging
import no.nav.saas.proxy.token.TokenValidation
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

const val NAIS_DEFAULT_PORT = 8080
const val NAIS_ISALIVE = "/internal/isAlive"
const val NAIS_ISREADY = "/internal/isReady"
const val NAIS_METRICS = "/internal/metrics"

const val API_URI_VAR = "rest"
const val API_INTERNAL_TEST_URI = "/internal/test/{$API_URI_VAR:.*}"
const val API_URI = "/{$API_URI_VAR:.*}"

const val TARGET_INGRESS = "target-ingress"
const val TARGET_CLIENT_ID = "target-client-id"
const val AUTHORIZATION = "Authorization"
const val HOST = "host"
const val X_FORWARDED_HOST = "x-forwarded-host"

const val env_WHITELIST_FILE = "WHITELIST_FILE"

object Application {
    private val log = KotlinLogging.logger { }

    val rules = Rules.parse(System.getenv(env_WHITELIST_FILE))

    val client = ApacheClient()

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
            val path = (req.path(API_URI_VAR) ?: "")
            Metrics.testApiCalls.labels(path).inc()
            log.info { "Test url called with path $path" }
            val method = req.method
            val targetIngress = req.requireHeader(TARGET_INGRESS)

            val team = rules.filter { it.value.keys.contains(targetIngress) }.map { it.key }.firstOrNull()

            if (team == null) {
                Response(NON_AUTHORITATIVE_INFORMATION).body("Ingress not found in rules. Not approved")
            } else {
                var approved = false
                var report = "Report:\n"
                Application.rules[team]?.let { it[targetIngress] }?.filter {
                    report += "Evaluating $it on method ${req.method}, path /$path "
                    it.evaluateAsRule(method, "/$path").also { report += "$it\n" }
                }?.firstOrNull()?.let {
                    approved = true
                }
                report += if (approved) "Approved" else "Not approved"
                Response(OK).body(report)
            }
        },
        API_URI bind { req: Request ->
            val path = req.path(API_URI_VAR) ?: ""
            Metrics.apiCalls.labels(path).inc()

            val targetIngress = req.requireHeader(TARGET_INGRESS)
            val targetClientId = req.requireHeader(TARGET_CLIENT_ID)

            File("/tmp/latestcall").writeText("Call:\nPath: $path\nMethod: ${req.method}\n Uri: ${req.uri}\nBody: ${req.body}\nHeaders: $${req.headers}")

            val team = rules.filter { it.value.keys.contains(targetIngress) }.map { it.key }.firstOrNull()

            val approvedByRules =
                if (team == null) {
                    false
                } else {
                    Application.rules[team]?.let { it[targetIngress] }?.filter {
                        it.evaluateAsRule(req.method, "/$path")
                    }?.firstOrNull()?.let {
                        true
                    } ?: false
                }

            if (!approvedByRules) {
                Response(BAD_REQUEST).body("Proxy: Bad request")
            } else if (!TokenValidation.containsValidToken(req, targetClientId)) {
                Response(UNAUTHORIZED).body("Proxy: Not authorized")
            } else {
                val blockFromForwarding = listOf(TARGET_INGRESS, TARGET_CLIENT_ID, HOST, X_FORWARDED_HOST)
                val forwardHeaders = req.headers.filter { !blockFromForwarding.contains(it.first) }.toList()
                val redirect = Request(req.method, "$targetIngress/${req.uri}").body(req.body).headers(forwardHeaders)
                log.info { "Forwarded call to ${req.method} $targetIngress/${req.uri}" }
                client(redirect)
            }
        }
    )
}
