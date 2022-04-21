package no.nav.saas.proxy

import mu.KotlinLogging
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer

private const val EV_bootstrapWaitTime = "MS_BETWEEN_WORK" // default to 10 minutes
// private val bootstrapWaitTime = AnEnvironment.getEnvOrDefault(EV_bootstrapWaitTime, "60000").toLong()

private val log = KotlinLogging.logger { }

object Application {
    // val gson = Gson()

    private val log = KotlinLogging.logger { }

    fun start() {
        log.info { "Starting" }
        apiServer(NAIS_DEFAULT_PORT).start()
        log.info { "Finished!" }
    }

    fun apiServer(port: Int): Http4kServer = api().asServer(Netty(port))

    fun api(): HttpHandler = routes(

        "/hello" bind Method.GET to {
            Response(Status.OK).body("Hello there")
        },
        // "/static" bind static(Classpath("/static")),
        /*
        "/innboks" bind Method.POST to {
            metrics.requestsInnboks.inc()
            log.info { "innboks called${if (devContext) " with body ${it.bodyString()}" else ""}" }
            if (containsValidToken(it)) {
                try {
                    val innboksRequest = Application.gson.fromJson(it.bodyString(), Array<InnboksRequest>::class.java)
                    val result: MutableList<InnboksInput> = mutableListOf()
                    innboksRequest.forEach {
                        val innboksbuilder = InnboksInputBuilder()
                            .withEksternVarsling(it.eksternVarsling)
                            .withSikkerhetsnivaa(it.sikkerhetsnivaa)
                            .withTekst(it.tekst)
                            .withTidspunkt(LocalDateTime.ofInstant(Instant.parse(it.tidspunkt), ZoneOffset.UTC))
                            .withEpostVarslingstekst(it.epostVarslingstekst)
                            .withEpostVarslingstittel(it.epostVarslingstittel)
                            .withSmsVarslingstekst(it.smsVarslingstekst)
                        if (it.link.isNotEmpty()) {
                            innboksbuilder.withLink(URL(it.link))
                        }
                        if (it.prefererteKanaler.isNotEmpty()) {
                            innboksbuilder.withPrefererteKanaler(
                                *it.prefererteKanaler.split(",").map { PreferertKanal.valueOf(it) }
                                    .toTypedArray()
                            )
                        }
                        val innboks = innboksbuilder.build()
                        Application.brukernotifikasjonService.sendInnboks(
                            it.eventId,
                            it.grupperingsId,
                            it.fodselsnummer,
                            innboks
                        )
                        result.add(innboks)
                    }
                    log.info("Published ${result.count()} Innboks events")
                    Response(Status.OK).body("Published ${result.count()} Innboks events ${if (devContext) result.toString() else ""}")
                } catch (e: Exception) {
                    /*
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    e.printStackTrace(pw)
                    val error = sw.toString()
                     */
                    log.error { e.toString() }
                    metrics.apiIssues.inc()
                    Response(Status.EXPECTATION_FAILED).body(e.toString())
                }
            } else {
                metrics.apiIssues.inc()
                log.info { "saas-proxy api call denied - missing valid token" }
                Response(Status.UNAUTHORIZED)
            }
        },
        "/done" bind Method.POST to {
            metrics.requestsDone.inc()
            log.info { "done called${if (devContext) " with body ${it.bodyString()}" else ""}" }
            if (containsValidToken(it)) {
                try {
                    val doneRequest = Application.gson.fromJson(it.bodyString(), Array<DoneRequest>::class.java)
                    val result: MutableList<DoneInput> = mutableListOf()
                    doneRequest.forEach {
                        val done = DoneInputBuilder()
                            .withTidspunkt(LocalDateTime.ofInstant(Instant.parse(it.tidspunkt), ZoneOffset.UTC))
                            .build()
                        Application.brukernotifikasjonService.sendDone(it.eventId, it.grupperingsId, it.fodselsnummer, done)
                        result.add(done)
                    }
                    Response(Status.OK).body("Published ${result.count()} Done events ${if (devContext) result.toString() else ""}")
                } catch (e: Exception) {
                    metrics.apiIssues.inc()
                    log.error { e }
                    Response(Status.EXPECTATION_FAILED)
                }
            } else {
                metrics.apiIssues.inc()
                log.info { "saas-proxy api call denied - missing valid token" }
                Response(Status.UNAUTHORIZED)
            }
        },
        */
        NAIS_ISALIVE bind Method.GET to { Response(Status.OK) },
        NAIS_ISREADY bind Method.GET to { Response(Status.OK) },
        NAIS_METRICS bind Method.GET to { Response(Status.OK)
            /*
            runCatching {
                StringWriter().let { str ->
                    TextFormat.write004(str, Metrics.cRegistry.metricFamilySamples())
                    str
                }.toString()
            }
                .onFailure {
                    log.error { "/prometheus failed writing metrics - ${it.localizedMessage}" }
                }
                .getOrDefault("")
                .responseByContent()*/
        },
        NAIS_PRESTOP bind Method.GET to {
            metrics.preStopHook.inc()
            // PrestopHook.activate()
            log.info { "Received PreStopHook from NAIS" }
            Response(Status.OK)
        }
    )

    const val NAIS_URL = "http://localhost:"
    const val NAIS_DEFAULT_PORT = 8080

    const val NAIS_ISALIVE = "/isAlive"
    const val NAIS_ISREADY = "/isReady"
    const val NAIS_METRICS = "/metrics"
    const val NAIS_PRESTOP = "/stop"

    val devContext: Boolean = System.getenv("SF_INSTANCE") == "PREPROD"

    private fun String.responseByContent(): Response =
        if (this.isNotEmpty()) Response(Status.OK).body(this) else Response(Status.NO_CONTENT)

    // Variant to use to run a job loop as base, having the api enabled only until job loop breaks:
    fun enableAPI(port: Int = NAIS_DEFAULT_PORT, doSomething: () -> Unit): Boolean =
        apiServer(port).let { srv ->
            try {
                srv.start().use {
                    log.info { "NAIS DSL is up and running at port $port" }
                    runCatching(doSomething)
                        .onFailure {
                            log.error { "Failure during doSomething in enableNAISAPI - ${it.localizedMessage}" }
                        }
                }
                true
            } catch (e: Exception) {
                log.error { "Failure during enable/disable NAIS api for port $port - ${e.localizedMessage}" }
                false
            } finally {
                srv.close()
                log.info { "NAIS DSL is stopped at port $port" }
            }
        }
}
