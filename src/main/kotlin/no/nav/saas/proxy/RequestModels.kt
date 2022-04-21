package no.nav.saas.proxy

data class DoneRequest(val eventId: String, val tidspunkt: String, val fodselsnummer: String, val grupperingsId: String)

data class InnboksRequest(
    val eventId: String,
    val eksternVarsling: Boolean = false,
    val link: String = "",
    val sikkerhetsnivaa: Int = 4,
    val tekst: String,
    val prefererteKanaler: String = "",
    val tidspunkt: String,
    val fodselsnummer: String,
    val grupperingsId: String,
    val epostVarslingstekst: String,
    val epostVarslingstittel: String,
    val smsVarslingstekst: String
)
